package app.ownplay.player.persistence.sync

import app.ownplay.player.persistence.CustomGroupEntity
import app.ownplay.player.persistence.CustomGroupMembershipEntity
import app.ownplay.player.persistence.FavoriteEntryEntity
import app.ownplay.player.persistence.OwnPlayDatabase
import java.util.UUID

/**
 * Writes local user mutations into the transport-neutral sync metadata tables.
 *
 * Callers should invoke these methods from the same Room transaction as the corresponding local
 * mutation so user-visible state and sync metadata commit or roll back together.
 */
class DeviceSyncLocalMutationWriter(
    private val database: OwnPlayDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val deviceIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun recordSourceCreatedOrRestored(localSourceId: String) {
        val source = requireNotNull(database.playlistSourceDao().getById(localSourceId))
        val clock = allocateClock()
        val dao = database.deviceSyncDao()
        val existing = dao.sourceByLocalId(localSourceId) ?: dao.sourceBySyncId(localSourceId)
        dao.upsertSource(
            if (existing == null) {
                DeviceSyncSourceEntity(
                    syncSourceId = localSourceId,
                    localSourceId = localSourceId,
                    sourceKind = source.sourceKind,
                    locatorFingerprint = null,
                    displayName = source.name,
                    displayNameUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                    displayNameRevision = clock.revision,
                    displayNameDeviceId = clock.deviceId,
                    enabled = source.enabled,
                    enabledUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                    enabledRevision = clock.revision,
                    enabledDeviceId = clock.deviceId,
                    deleted = false,
                    deletedUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                    deletedRevision = clock.revision,
                    deletedDeviceId = clock.deviceId,
                )
            } else {
                existing.copy(
                    localSourceId = localSourceId,
                    displayName = source.name,
                    displayNameUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                    displayNameRevision = clock.revision,
                    displayNameDeviceId = clock.deviceId,
                    enabled = source.enabled,
                    enabledUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                    enabledRevision = clock.revision,
                    enabledDeviceId = clock.deviceId,
                    deleted = false,
                    deletedUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                    deletedRevision = clock.revision,
                    deletedDeviceId = clock.deviceId,
                )
            },
        )
    }

    suspend fun recordSourceRenamed(localSourceId: String, displayName: String) {
        val normalized = displayName.trim()
        require(normalized.isNotEmpty())
        val existing = ensureSource(localSourceId)
        val clock = allocateClock()
        database.deviceSyncDao().upsertSource(
            existing.copy(
                displayName = normalized,
                displayNameUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                displayNameRevision = clock.revision,
                displayNameDeviceId = clock.deviceId,
            ),
        )
    }

    suspend fun recordSourceDeleted(localSourceId: String) {
        val existing = ensureSource(localSourceId)
        val clock = allocateClock()
        database.deviceSyncDao().upsertSource(
            existing.copy(
                localSourceId = null,
                deleted = true,
                deletedUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                deletedRevision = clock.revision,
                deletedDeviceId = clock.deviceId,
            ),
        )
    }

    suspend fun recordLocalDisplayName(
        sourceId: String,
        channelId: String,
        localDisplayName: String?,
    ) {
        val clock = allocateClock()
        val channel = channelSyncRow(sourceId, channelId)
        database.deviceSyncDao().upsertChannel(
            channel.copy(
                localDisplayName = localDisplayName,
                localDisplayNameUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                localDisplayNameRevision = clock.revision,
                localDisplayNameDeviceId = clock.deviceId,
            ),
        )
    }

    suspend fun recordHidden(
        sourceId: String,
        channelIds: Collection<String>,
        hidden: Boolean,
    ) {
        if (channelIds.isEmpty()) return
        val clock = allocateClock()
        val rows = channelIds.map { channelId ->
            channelSyncRow(sourceId, channelId).copy(
                hidden = hidden,
                hiddenUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                hiddenRevision = clock.revision,
                hiddenDeviceId = clock.deviceId,
            )
        }
        database.deviceSyncDao().upsertChannels(rows)
    }

    suspend fun recordFavorites(
        sourceId: String,
        activeEntries: List<FavoriteEntryEntity>,
        removedChannelIds: Collection<String> = emptyList(),
    ) {
        if (activeEntries.isEmpty() && removedChannelIds.isEmpty()) return
        val clock = allocateClock()
        val activeRows = activeEntries.map { entry ->
            channelSyncRow(sourceId, entry.channelId).copy(
                favoriteOrder = entry.favoriteOrder,
                favoriteAddedAtEpochMillis = entry.addedAtEpochMillis,
                favoriteUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                favoriteRevision = clock.revision,
                favoriteDeviceId = clock.deviceId,
            )
        }
        val removedRows = removedChannelIds.map { channelId ->
            channelSyncRow(sourceId, channelId).copy(
                favoriteOrder = null,
                favoriteAddedAtEpochMillis = null,
                favoriteUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                favoriteRevision = clock.revision,
                favoriteDeviceId = clock.deviceId,
            )
        }
        database.deviceSyncDao().upsertChannels((activeRows + removedRows).distinctBy { row ->
            row.syncSourceId to row.providerKey
        })
    }

    suspend fun recordManualOrder(
        sourceId: String,
        assignments: Map<String, Long>,
    ) {
        if (assignments.isEmpty()) return
        val clock = allocateClock()
        val rows = assignments.map { (channelId, manualOrder) ->
            require(manualOrder >= 0L)
            channelSyncRow(sourceId, channelId).copy(
                manualOrder = manualOrder,
                manualOrderUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                manualOrderRevision = clock.revision,
                manualOrderDeviceId = clock.deviceId,
            )
        }
        database.deviceSyncDao().upsertChannels(rows)
    }

    suspend fun recordGroupCreated(group: CustomGroupEntity) {
        val clock = allocateClock()
        database.deviceSyncDao().upsertGroup(
            DeviceSyncGroupEntity(
                syncGroupId = group.groupId,
                name = group.name,
                nameUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                nameRevision = clock.revision,
                nameDeviceId = clock.deviceId,
                groupOrder = group.groupOrder,
                groupOrderUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                groupOrderRevision = clock.revision,
                groupOrderDeviceId = clock.deviceId,
                deleted = false,
                deletedUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                deletedRevision = clock.revision,
                deletedDeviceId = clock.deviceId,
            ),
        )
    }

    suspend fun recordGroupRenamed(group: CustomGroupEntity) {
        val existing = ensureGroup(group)
        val clock = allocateClock()
        database.deviceSyncDao().upsertGroup(
            existing.copy(
                name = group.name,
                nameUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                nameRevision = clock.revision,
                nameDeviceId = clock.deviceId,
            ),
        )
    }

    suspend fun recordGroupDeleted(group: CustomGroupEntity) {
        val existing = ensureGroup(group)
        val clock = allocateClock()
        database.deviceSyncDao().upsertGroup(
            existing.copy(
                deleted = true,
                deletedUpdatedAtEpochMillis = clock.updatedAtEpochMillis,
                deletedRevision = clock.revision,
                deletedDeviceId = clock.deviceId,
            ),
        )
    }

    suspend fun recordMemberships(
        groupId: String,
        activeMemberships: List<CustomGroupMembershipEntity>,
        removedChannelIds: Collection<String> = emptyList(),
    ) {
        if (activeMemberships.isEmpty() && removedChannelIds.isEmpty()) return
        val clock = allocateClock()
        val activeRows = activeMemberships.map { membership ->
            membershipRow(
                groupId = groupId,
                channelId = membership.channelId,
                groupOrder = membership.groupOrder,
                clock = clock,
            )
        }
        val removedRows = removedChannelIds.map { channelId ->
            membershipRow(
                groupId = groupId,
                channelId = channelId,
                groupOrder = null,
                clock = clock,
            )
        }
        database.deviceSyncDao().upsertMemberships((activeRows + removedRows).distinctBy { row ->
            Triple(row.syncGroupId, row.syncSourceId, row.providerKey)
        })
    }

    private suspend fun membershipRow(
        groupId: String,
        channelId: String,
        groupOrder: Long?,
        clock: LocalSyncClock,
    ): DeviceSyncGroupMembershipEntity {
        val channel = requireNotNull(database.providerCatalogDao().channelById(channelId))
        val syncSource = ensureSource(channel.sourceId)
        return DeviceSyncGroupMembershipEntity(
            syncGroupId = groupId,
            syncSourceId = syncSource.syncSourceId,
            providerKey = channel.providerKey,
            groupOrder = groupOrder,
            updatedAtEpochMillis = clock.updatedAtEpochMillis,
            revision = clock.revision,
            deviceId = clock.deviceId,
        )
    }

    private suspend fun channelSyncRow(sourceId: String, channelId: String): DeviceSyncChannelEntity {
        val source = ensureSource(sourceId)
        val providerKey = providerKey(sourceId, channelId)
        return database.deviceSyncDao().channel(source.syncSourceId, providerKey)
            ?: DeviceSyncChannelEntity(
                syncSourceId = source.syncSourceId,
                providerKey = providerKey,
            )
    }

    private suspend fun providerKey(sourceId: String, channelId: String): String {
        val channel = requireNotNull(database.providerCatalogDao().channelById(channelId))
        require(channel.sourceId == sourceId) { "Channel does not belong to requested source" }
        return channel.providerKey
    }

    private suspend fun ensureSource(localSourceId: String): DeviceSyncSourceEntity {
        val dao = database.deviceSyncDao()
        dao.sourceByLocalId(localSourceId)?.let { return it }
        recordSourceCreatedOrRestored(localSourceId)
        return requireNotNull(dao.sourceByLocalId(localSourceId))
    }

    private suspend fun ensureGroup(group: CustomGroupEntity): DeviceSyncGroupEntity {
        database.deviceSyncDao().allGroups().firstOrNull { it.syncGroupId == group.groupId }?.let {
            return it
        }
        recordGroupCreated(group)
        return requireNotNull(
            database.deviceSyncDao().allGroups().firstOrNull { it.syncGroupId == group.groupId },
        )
    }

    private suspend fun allocateClock(): LocalSyncClock {
        val dao = database.deviceSyncDao()
        val timestamp = now().coerceAtLeast(0L)
        val current = dao.localState() ?: DeviceSyncLocalStateEntity(
            deviceId = deviceIdFactory(),
            nextRevision = 1L,
            updatedAtEpochMillis = timestamp,
        )
        val revision = current.nextRevision
        dao.upsertLocalState(
            current.copy(
                nextRevision = revision + 1L,
                updatedAtEpochMillis = timestamp,
            ),
        )
        return LocalSyncClock(
            updatedAtEpochMillis = timestamp,
            revision = revision,
            deviceId = current.deviceId,
        )
    }
}

private data class LocalSyncClock(
    val updatedAtEpochMillis: Long,
    val revision: Long,
    val deviceId: String,
)
