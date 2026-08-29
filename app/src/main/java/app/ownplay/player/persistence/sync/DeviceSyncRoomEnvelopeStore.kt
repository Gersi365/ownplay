package app.ownplay.player.persistence.sync

import androidx.room.withTransaction
import app.ownplay.player.persistence.ChannelCustomizationEntity
import app.ownplay.player.persistence.CustomGroupEntity
import app.ownplay.player.persistence.CustomGroupMembershipEntity
import app.ownplay.player.persistence.FavoriteEntryEntity
import app.ownplay.player.persistence.HiddenEntryEntity
import app.ownplay.player.persistence.OwnPlayDatabase
import app.ownplay.player.sync.DEVICE_SYNC_CONTRACT_VERSION
import app.ownplay.player.sync.DeviceSyncEnvelope
import app.ownplay.player.sync.SyncChannelKey
import app.ownplay.player.sync.SyncChannelState
import app.ownplay.player.sync.SyncClock
import app.ownplay.player.sync.SyncFavoriteState
import app.ownplay.player.sync.SyncGroupKey
import app.ownplay.player.sync.SyncGroupMembershipKey
import app.ownplay.player.sync.SyncGroupMembershipState
import app.ownplay.player.sync.SyncGroupState
import app.ownplay.player.sync.SyncSourceIdentity
import app.ownplay.player.sync.SyncSourceState
import app.ownplay.player.sync.SyncValue
import kotlin.math.max

/**
 * Bridges transport-neutral sync envelopes and the existing local-first Room model.
 *
 * Remote apply deliberately bypasses normal user mutators. Those mutators allocate a fresh local
 * revision for user actions; doing that while applying a merged remote clock would create a sync
 * feedback loop. Instead, merged metadata and its materialized UI state are committed together in
 * one Room transaction.
 *
 * Playlist locator/credential transport is intentionally out of scope. A remote source that is not
 * already materialized locally therefore remains deferred until a secure secret transport exists.
 * A remote source deletion is soft-materialized by disabling the existing local source; secure
 * locator/credential cleanup is deferred to the source lifecycle layer.
 */
class DeviceSyncRoomEnvelopeStore(
    private val database: OwnPlayDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun readEnvelope(): DeviceSyncEnvelope = database.withTransaction {
        val syncDao = database.deviceSyncDao()
        val localState = requireNotNull(syncDao.localState()) {
            "Device sync local state is missing"
        }
        val sources = syncDao.allSources()
        val channels = buildList {
            for (source in sources) {
                addAll(syncDao.channelsForSource(source.syncSourceId))
            }
        }
        val groups = syncDao.allGroups()
        val memberships = syncDao.allMemberships()

        DeviceSyncEnvelope(
            contractVersion = DEVICE_SYNC_CONTRACT_VERSION,
            generatedAtEpochMillis = now().coerceAtLeast(0L),
            deviceId = localState.deviceId,
            sources = sources.map(DeviceSyncSourceEntity::toContract),
            channels = channels.map(DeviceSyncChannelEntity::toContract),
            groups = groups.map(DeviceSyncGroupEntity::toContract),
            memberships = memberships.map(DeviceSyncGroupMembershipEntity::toContract),
        )
    }

    suspend fun applyMergedEnvelope(envelope: DeviceSyncEnvelope): DeviceSyncApplyResult =
        database.withTransaction {
            require(envelope.contractVersion == DEVICE_SYNC_CONTRACT_VERSION)
            val syncDao = database.deviceSyncDao()
            val localState = requireNotNull(syncDao.localState()) {
                "Device sync local state is missing"
            }

            val existingSources = syncDao.allSources().associateBy { it.syncSourceId }
            val sourceRows = envelope.sources.map { state ->
                state.toPersistence(existingSources[state.identity.syncSourceId]?.localSourceId)
            }
            val channelRows = envelope.channels.map(SyncChannelState::toPersistence)
            val groupRows = envelope.groups.map(SyncGroupState::toPersistence)
            val membershipRows = envelope.memberships.map(SyncGroupMembershipState::toPersistence)

            syncDao.upsertSources(sourceRows)
            syncDao.upsertChannels(channelRows)
            syncDao.upsertGroups(groupRows)
            syncDao.upsertMemberships(membershipRows)

            val deferred = mutableListOf<DeviceSyncDeferredMaterialization>()
            var sourceCount = 0
            var channelCount = 0
            var groupCount = 0
            var membershipCount = 0

            val appliedSources = sourceRows.associateBy(DeviceSyncSourceEntity::syncSourceId)
            for (state in envelope.sources.sortedBy { it.identity.syncSourceId }) {
                val row = requireNotNull(appliedSources[state.identity.syncSourceId])
                val localSourceId = row.localSourceId
                if (localSourceId == null) {
                    if (!row.deleted) {
                        deferred += DeviceSyncDeferredMaterialization(
                            key = row.syncSourceId,
                            reason = DeviceSyncDeferredReason.SOURCE_SECRET_REQUIRED,
                        )
                    }
                    continue
                }

                val localSource = database.playlistSourceDao().getById(localSourceId)
                if (localSource == null) {
                    deferred += DeviceSyncDeferredMaterialization(
                        key = row.syncSourceId,
                        reason = DeviceSyncDeferredReason.LOCAL_SOURCE_MISSING,
                    )
                    continue
                }
                require(localSource.sourceKind == row.sourceKind) {
                    "Local source kind does not match merged sync identity"
                }

                val updatedAt = max(
                    row.displayNameUpdatedAtEpochMillis,
                    max(row.enabledUpdatedAtEpochMillis, row.deletedUpdatedAtEpochMillis),
                )
                database.playlistSourceDao().upsert(
                    localSource.copy(
                        name = row.displayName,
                        enabled = if (row.deleted) false else row.enabled,
                        updatedAtEpochMillis = max(localSource.updatedAtEpochMillis, updatedAt),
                    ),
                )
                sourceCount += 1
                if (row.deleted) {
                    deferred += DeviceSyncDeferredMaterialization(
                        key = row.syncSourceId,
                        reason = DeviceSyncDeferredReason.SOURCE_SECURE_PURGE_REQUIRED,
                    )
                }
            }

            for (state in envelope.channels.sortedBy(SyncChannelState::key)) {
                val source = appliedSources[state.key.syncSourceId]
                val localSourceId = source?.localSourceId
                if (source == null || source.deleted || localSourceId == null) {
                    if (state.hasMaterializableState()) {
                        deferred += DeviceSyncDeferredMaterialization(
                            key = "${state.key.syncSourceId}:${state.key.providerKey}",
                            reason = DeviceSyncDeferredReason.CHANNEL_SOURCE_NOT_MATERIALIZED,
                        )
                    }
                    continue
                }
                val channel = database.providerCatalogDao().channelByProviderKey(
                    sourceId = localSourceId,
                    providerKey = state.key.providerKey,
                )
                if (channel == null) {
                    if (state.hasMaterializableState()) {
                        deferred += DeviceSyncDeferredMaterialization(
                            key = "${state.key.syncSourceId}:${state.key.providerKey}",
                            reason = DeviceSyncDeferredReason.PROVIDER_CHANNEL_MISSING,
                        )
                    }
                    continue
                }

                applyChannelState(
                    sourceId = localSourceId,
                    channelId = channel.channelId,
                    state = state,
                )
                channelCount += 1
            }

            for (state in envelope.groups.sortedBy(SyncGroupState::key)) {
                val deleted = requireNotNull(state.deleted.value) {
                    "Group deleted state must be explicit"
                }
                if (deleted) {
                    database.personalizationDao().deleteCustomGroup(state.key.syncGroupId)
                    groupCount += 1
                    continue
                }
                val name = requireNotNull(state.name.value) {
                    "Active group requires a name"
                }
                val order = requireNotNull(state.groupOrder.value) {
                    "Active group requires an order"
                }
                val existing = database.personalizationDao().customGroupById(state.key.syncGroupId)
                database.personalizationDao().upsertGroup(
                    CustomGroupEntity(
                        groupId = state.key.syncGroupId,
                        name = name,
                        groupOrder = order,
                        createdAtEpochMillis = existing?.createdAtEpochMillis
                            ?: minOf(
                                state.name.clock.updatedAtEpochMillis,
                                state.groupOrder.clock.updatedAtEpochMillis,
                                state.deleted.clock.updatedAtEpochMillis,
                            ),
                    ),
                )
                groupCount += 1
            }

            val groupStates = envelope.groups.associateBy { it.key.syncGroupId }
            for (state in envelope.memberships.sortedBy(SyncGroupMembershipState::key)) {
                val groupState = groupStates[state.key.groupKey.syncGroupId]
                if (groupState?.deleted?.value == true) continue
                if (database.personalizationDao().customGroupById(state.key.groupKey.syncGroupId) == null) {
                    if (state.order.value != null) {
                        deferred += DeviceSyncDeferredMaterialization(
                            key = state.key.toStableString(),
                            reason = DeviceSyncDeferredReason.GROUP_NOT_MATERIALIZED,
                        )
                    }
                    continue
                }

                val source = appliedSources[state.key.channelKey.syncSourceId]
                val localSourceId = source?.localSourceId
                if (source == null || source.deleted || localSourceId == null) {
                    if (state.order.value != null) {
                        deferred += DeviceSyncDeferredMaterialization(
                            key = state.key.toStableString(),
                            reason = DeviceSyncDeferredReason.CHANNEL_SOURCE_NOT_MATERIALIZED,
                        )
                    }
                    continue
                }
                val channel = database.providerCatalogDao().channelByProviderKey(
                    sourceId = localSourceId,
                    providerKey = state.key.channelKey.providerKey,
                )
                if (channel == null) {
                    if (state.order.value != null) {
                        deferred += DeviceSyncDeferredMaterialization(
                            key = state.key.toStableString(),
                            reason = DeviceSyncDeferredReason.PROVIDER_CHANNEL_MISSING,
                        )
                    }
                    continue
                }

                val order = state.order.value
                if (order == null) {
                    database.personalizationDao().removeGroupMembership(
                        state.key.groupKey.syncGroupId,
                        channel.channelId,
                    )
                } else {
                    database.personalizationDao().upsertGroupMembership(
                        CustomGroupMembershipEntity(
                            groupId = state.key.groupKey.syncGroupId,
                            channelId = channel.channelId,
                            groupOrder = order,
                        ),
                    )
                }
                membershipCount += 1
            }

            val maxOwnRevision = envelope.allClocks()
                .asSequence()
                .filter { it.deviceId == localState.deviceId }
                .maxOfOrNull(SyncClock::revision)
                ?: 0L
            syncDao.upsertLocalState(
                localState.copy(
                    nextRevision = max(localState.nextRevision, maxOwnRevision + 1L),
                    updatedAtEpochMillis = max(
                        localState.updatedAtEpochMillis,
                        envelope.generatedAtEpochMillis,
                    ),
                ),
            )

            DeviceSyncApplyResult(
                sourcesMaterialized = sourceCount,
                channelsMaterialized = channelCount,
                groupsMaterialized = groupCount,
                membershipsMaterialized = membershipCount,
                deferred = deferred
                    .distinct()
                    .sortedWith(compareBy(DeviceSyncDeferredMaterialization::reason, DeviceSyncDeferredMaterialization::key)),
            )
        }

    private suspend fun applyChannelState(
        sourceId: String,
        channelId: String,
        state: SyncChannelState,
    ) {
        val dao = database.personalizationDao()
        if (state.localDisplayName != null || state.manualOrder != null) {
            val existing = dao.customizationForChannel(sourceId, channelId)
            val updated = ChannelCustomizationEntity(
                channelId = channelId,
                localDisplayName = state.localDisplayName?.value ?: existing?.localDisplayName,
                logoOverrideRef = existing?.logoOverrideRef,
                manualOrder = state.manualOrder?.value ?: existing?.manualOrder,
            ).let { candidate ->
                candidate.copy(
                    localDisplayName = if (state.localDisplayName != null) {
                        state.localDisplayName.value
                    } else {
                        candidate.localDisplayName
                    },
                    manualOrder = if (state.manualOrder != null) {
                        state.manualOrder.value
                    } else {
                        candidate.manualOrder
                    },
                )
            }
            if (
                existing != null ||
                updated.localDisplayName != null ||
                updated.logoOverrideRef != null ||
                updated.manualOrder != null
            ) {
                dao.upsertCustomization(updated)
            }
        }

        state.hidden?.let { hidden ->
            if (hidden.value == true) {
                dao.upsertHidden(
                    HiddenEntryEntity(
                        channelId = channelId,
                        hiddenAtEpochMillis = hidden.clock.updatedAtEpochMillis,
                    ),
                )
            } else {
                dao.unhide(channelId)
            }
        }

        state.favorite?.let { favorite ->
            val value = favorite.value
            if (value == null) {
                dao.removeFavorite(channelId)
            } else {
                dao.upsertFavorite(
                    FavoriteEntryEntity(
                        channelId = channelId,
                        favoriteOrder = value.order,
                        addedAtEpochMillis = value.addedAtEpochMillis,
                    ),
                )
            }
        }
    }
}

data class DeviceSyncApplyResult(
    val sourcesMaterialized: Int,
    val channelsMaterialized: Int,
    val groupsMaterialized: Int,
    val membershipsMaterialized: Int,
    val deferred: List<DeviceSyncDeferredMaterialization>,
)

data class DeviceSyncDeferredMaterialization(
    val key: String,
    val reason: DeviceSyncDeferredReason,
)

enum class DeviceSyncDeferredReason {
    SOURCE_SECRET_REQUIRED,
    SOURCE_SECURE_PURGE_REQUIRED,
    LOCAL_SOURCE_MISSING,
    CHANNEL_SOURCE_NOT_MATERIALIZED,
    PROVIDER_CHANNEL_MISSING,
    GROUP_NOT_MATERIALIZED,
}

private fun DeviceSyncSourceEntity.toContract(): SyncSourceState = SyncSourceState(
    identity = SyncSourceIdentity(
        syncSourceId = syncSourceId,
        sourceKind = sourceKind,
        locatorFingerprint = locatorFingerprint,
    ),
    displayName = SyncValue(
        value = displayName,
        clock = SyncClock(displayNameUpdatedAtEpochMillis, displayNameRevision, displayNameDeviceId),
    ),
    enabled = SyncValue(
        value = enabled,
        clock = SyncClock(enabledUpdatedAtEpochMillis, enabledRevision, enabledDeviceId),
    ),
    deleted = SyncValue(
        value = deleted,
        clock = SyncClock(deletedUpdatedAtEpochMillis, deletedRevision, deletedDeviceId),
    ),
    encryptedSecretRef = encryptedSecretUpdatedAtEpochMillis?.let { updatedAt ->
        SyncValue(
            value = encryptedSecretRef,
            clock = SyncClock(
                updatedAtEpochMillis = updatedAt,
                revision = requireNotNull(encryptedSecretRevision),
                deviceId = requireNotNull(encryptedSecretDeviceId),
            ),
        )
    },
)

private fun DeviceSyncChannelEntity.toContract(): SyncChannelState = SyncChannelState(
    key = SyncChannelKey(syncSourceId = syncSourceId, providerKey = providerKey),
    localDisplayName = localDisplayNameUpdatedAtEpochMillis?.let { updatedAt ->
        SyncValue(
            value = localDisplayName,
            clock = SyncClock(updatedAt, requireNotNull(localDisplayNameRevision), requireNotNull(localDisplayNameDeviceId)),
        )
    },
    manualOrder = manualOrderUpdatedAtEpochMillis?.let { updatedAt ->
        SyncValue(
            value = manualOrder,
            clock = SyncClock(updatedAt, requireNotNull(manualOrderRevision), requireNotNull(manualOrderDeviceId)),
        )
    },
    hidden = hiddenUpdatedAtEpochMillis?.let { updatedAt ->
        SyncValue(
            value = hidden,
            clock = SyncClock(updatedAt, requireNotNull(hiddenRevision), requireNotNull(hiddenDeviceId)),
        )
    },
    favorite = favoriteUpdatedAtEpochMillis?.let { updatedAt ->
        SyncValue(
            value = favoriteOrder?.let { order ->
                SyncFavoriteState(
                    order = order,
                    addedAtEpochMillis = requireNotNull(favoriteAddedAtEpochMillis),
                )
            },
            clock = SyncClock(updatedAt, requireNotNull(favoriteRevision), requireNotNull(favoriteDeviceId)),
        )
    },
)

private fun DeviceSyncGroupEntity.toContract(): SyncGroupState = SyncGroupState(
    key = SyncGroupKey(syncGroupId),
    name = SyncValue(
        value = name,
        clock = SyncClock(nameUpdatedAtEpochMillis, nameRevision, nameDeviceId),
    ),
    groupOrder = SyncValue(
        value = groupOrder,
        clock = SyncClock(groupOrderUpdatedAtEpochMillis, groupOrderRevision, groupOrderDeviceId),
    ),
    deleted = SyncValue(
        value = deleted,
        clock = SyncClock(deletedUpdatedAtEpochMillis, deletedRevision, deletedDeviceId),
    ),
)

private fun DeviceSyncGroupMembershipEntity.toContract(): SyncGroupMembershipState =
    SyncGroupMembershipState(
        key = SyncGroupMembershipKey(
            groupKey = SyncGroupKey(syncGroupId),
            channelKey = SyncChannelKey(syncSourceId, providerKey),
        ),
        order = SyncValue(
            value = groupOrder,
            clock = SyncClock(updatedAtEpochMillis, revision, deviceId),
        ),
    )

private fun SyncSourceState.toPersistence(localSourceId: String?): DeviceSyncSourceEntity {
    val displayNameValue = requireNotNull(displayName.value) {
        "Source display name cannot be tombstoned"
    }
    val enabledValue = requireNotNull(enabled.value) {
        "Source enabled state cannot be tombstoned"
    }
    val deletedValue = requireNotNull(deleted.value) {
        "Source deleted state cannot be tombstoned"
    }
    return DeviceSyncSourceEntity(
        syncSourceId = identity.syncSourceId,
        localSourceId = localSourceId,
        sourceKind = identity.sourceKind,
        locatorFingerprint = identity.locatorFingerprint,
        displayName = displayNameValue,
        displayNameUpdatedAtEpochMillis = displayName.clock.updatedAtEpochMillis,
        displayNameRevision = displayName.clock.revision,
        displayNameDeviceId = displayName.clock.deviceId,
        enabled = enabledValue,
        enabledUpdatedAtEpochMillis = enabled.clock.updatedAtEpochMillis,
        enabledRevision = enabled.clock.revision,
        enabledDeviceId = enabled.clock.deviceId,
        deleted = deletedValue,
        deletedUpdatedAtEpochMillis = deleted.clock.updatedAtEpochMillis,
        deletedRevision = deleted.clock.revision,
        deletedDeviceId = deleted.clock.deviceId,
        encryptedSecretRef = encryptedSecretRef?.value,
        encryptedSecretUpdatedAtEpochMillis = encryptedSecretRef?.clock?.updatedAtEpochMillis,
        encryptedSecretRevision = encryptedSecretRef?.clock?.revision,
        encryptedSecretDeviceId = encryptedSecretRef?.clock?.deviceId,
    )
}

private fun SyncChannelState.toPersistence(): DeviceSyncChannelEntity = DeviceSyncChannelEntity(
    syncSourceId = key.syncSourceId,
    providerKey = key.providerKey,
    localDisplayName = localDisplayName?.value,
    localDisplayNameUpdatedAtEpochMillis = localDisplayName?.clock?.updatedAtEpochMillis,
    localDisplayNameRevision = localDisplayName?.clock?.revision,
    localDisplayNameDeviceId = localDisplayName?.clock?.deviceId,
    manualOrder = manualOrder?.value,
    manualOrderUpdatedAtEpochMillis = manualOrder?.clock?.updatedAtEpochMillis,
    manualOrderRevision = manualOrder?.clock?.revision,
    manualOrderDeviceId = manualOrder?.clock?.deviceId,
    hidden = hidden?.value,
    hiddenUpdatedAtEpochMillis = hidden?.clock?.updatedAtEpochMillis,
    hiddenRevision = hidden?.clock?.revision,
    hiddenDeviceId = hidden?.clock?.deviceId,
    favoriteOrder = favorite?.value?.order,
    favoriteAddedAtEpochMillis = favorite?.value?.addedAtEpochMillis,
    favoriteUpdatedAtEpochMillis = favorite?.clock?.updatedAtEpochMillis,
    favoriteRevision = favorite?.clock?.revision,
    favoriteDeviceId = favorite?.clock?.deviceId,
)

private fun SyncGroupState.toPersistence(): DeviceSyncGroupEntity = DeviceSyncGroupEntity(
    syncGroupId = key.syncGroupId,
    name = requireNotNull(name.value) { "Group name cannot be tombstoned in persistence" },
    nameUpdatedAtEpochMillis = name.clock.updatedAtEpochMillis,
    nameRevision = name.clock.revision,
    nameDeviceId = name.clock.deviceId,
    groupOrder = requireNotNull(groupOrder.value) { "Group order cannot be tombstoned in persistence" },
    groupOrderUpdatedAtEpochMillis = groupOrder.clock.updatedAtEpochMillis,
    groupOrderRevision = groupOrder.clock.revision,
    groupOrderDeviceId = groupOrder.clock.deviceId,
    deleted = requireNotNull(deleted.value) { "Group deleted state must be explicit" },
    deletedUpdatedAtEpochMillis = deleted.clock.updatedAtEpochMillis,
    deletedRevision = deleted.clock.revision,
    deletedDeviceId = deleted.clock.deviceId,
)

private fun SyncGroupMembershipState.toPersistence(): DeviceSyncGroupMembershipEntity =
    DeviceSyncGroupMembershipEntity(
        syncGroupId = key.groupKey.syncGroupId,
        syncSourceId = key.channelKey.syncSourceId,
        providerKey = key.channelKey.providerKey,
        groupOrder = order.value,
        updatedAtEpochMillis = order.clock.updatedAtEpochMillis,
        revision = order.clock.revision,
        deviceId = order.clock.deviceId,
    )

private fun SyncChannelState.hasMaterializableState(): Boolean =
    localDisplayName != null || manualOrder != null || hidden != null || favorite != null

private fun SyncGroupMembershipKey.toStableString(): String =
    "${groupKey.syncGroupId}:${channelKey.syncSourceId}:${channelKey.providerKey}"

private fun DeviceSyncEnvelope.allClocks(): List<SyncClock> = buildList {
    for (source in sources) {
        add(source.displayName.clock)
        add(source.enabled.clock)
        add(source.deleted.clock)
        source.encryptedSecretRef?.clock?.let(::add)
    }
    for (channel in channels) {
        channel.localDisplayName?.clock?.let(::add)
        channel.manualOrder?.clock?.let(::add)
        channel.hidden?.clock?.let(::add)
        channel.favorite?.clock?.let(::add)
    }
    for (group in groups) {
        add(group.name.clock)
        add(group.groupOrder.clock)
        add(group.deleted.clock)
    }
    for (membership in memberships) {
        add(membership.order.clock)
    }
}
