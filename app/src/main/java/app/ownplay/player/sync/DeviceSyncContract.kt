package app.ownplay.player.sync

/**
 * Transport-neutral contract for future OwnPlay cross-device synchronization.
 *
 * This layer deliberately contains no backend, authentication, Room migration, or credential
 * transport implementation. It defines the minimum state needed to merge phone/TV changes
 * deterministically before any remote transport is selected.
 */
internal const val DEVICE_SYNC_CONTRACT_VERSION = 1

data class SyncClock(
    val updatedAtEpochMillis: Long,
    val revision: Long,
    val deviceId: String,
) {
    init {
        require(updatedAtEpochMillis >= 0L)
        require(revision >= 0L)
        require(deviceId.isNotBlank())
    }
}

/**
 * A null value is an explicit tombstone. Tombstones are required so removing a favorite,
 * unhiding a channel, clearing an override, or removing a group membership can win over an
 * older remote value.
 */
data class SyncValue<T>(
    val value: T?,
    val clock: SyncClock,
)

data class SyncSourceIdentity(
    val syncSourceId: String,
    val sourceKind: String,
    /** Optional non-secret hash used only to reconcile a source independently added on a device. */
    val locatorFingerprint: String? = null,
) {
    init {
        require(syncSourceId.isNotBlank())
        require(sourceKind.isNotBlank())
        locatorFingerprint?.let { require(it.isNotBlank()) }
    }
}

data class SyncSourceState(
    val identity: SyncSourceIdentity,
    val displayName: SyncValue<String>,
    val enabled: SyncValue<Boolean>,
    /** true means this source was explicitly removed and must not be resurrected by older data. */
    val deleted: SyncValue<Boolean>,
    /**
     * Reference to an encrypted transport envelope containing portable locator/credential data.
     * The reference itself is versioned because endpoint/credentials can be replaced.
     */
    val encryptedSecretRef: SyncValue<String>? = null,
) {
    init {
        displayName.value?.let { require(it.isNotBlank()) }
        encryptedSecretRef?.value?.let { require(it.isNotBlank()) }
    }
}

data class SyncChannelKey(
    val syncSourceId: String,
    val providerKey: String,
) : Comparable<SyncChannelKey> {
    init {
        require(syncSourceId.isNotBlank())
        require(providerKey.isNotBlank())
    }

    override fun compareTo(other: SyncChannelKey): Int {
        val source = syncSourceId.compareTo(other.syncSourceId)
        return if (source != 0) source else providerKey.compareTo(other.providerKey)
    }
}

data class SyncFavoriteState(
    val order: Long,
    val addedAtEpochMillis: Long,
) {
    init {
        require(order >= 0L)
        require(addedAtEpochMillis >= 0L)
    }
}

data class SyncChannelState(
    val key: SyncChannelKey,
    val localDisplayName: SyncValue<String>? = null,
    val manualOrder: SyncValue<Long>? = null,
    val hidden: SyncValue<Boolean>? = null,
    val favorite: SyncValue<SyncFavoriteState>? = null,
) {
    init {
        manualOrder?.value?.let { require(it >= 0L) }
        localDisplayName?.value?.let { require(it.isNotBlank()) }
    }
}

data class SyncGroupKey(
    val syncGroupId: String,
) : Comparable<SyncGroupKey> {
    init {
        require(syncGroupId.isNotBlank())
    }

    override fun compareTo(other: SyncGroupKey): Int = syncGroupId.compareTo(other.syncGroupId)
}

data class SyncGroupState(
    val key: SyncGroupKey,
    val name: SyncValue<String>,
    val groupOrder: SyncValue<Long>,
    /** true means the group was explicitly removed. */
    val deleted: SyncValue<Boolean>,
) {
    init {
        name.value?.let { require(it.isNotBlank()) }
        groupOrder.value?.let { require(it >= 0L) }
    }
}

data class SyncGroupMembershipKey(
    val groupKey: SyncGroupKey,
    val channelKey: SyncChannelKey,
) : Comparable<SyncGroupMembershipKey> {
    override fun compareTo(other: SyncGroupMembershipKey): Int {
        val group = groupKey.compareTo(other.groupKey)
        return if (group != 0) group else channelKey.compareTo(other.channelKey)
    }
}

data class SyncGroupMembershipState(
    val key: SyncGroupMembershipKey,
    /** null = removed membership; non-null = order inside the group. */
    val order: SyncValue<Long>,
) {
    init {
        order.value?.let { require(it >= 0L) }
    }
}

data class DeviceSyncEnvelope(
    val contractVersion: Int = DEVICE_SYNC_CONTRACT_VERSION,
    val generatedAtEpochMillis: Long,
    val deviceId: String,
    val sources: List<SyncSourceState>,
    val channels: List<SyncChannelState>,
    val groups: List<SyncGroupState>,
    val memberships: List<SyncGroupMembershipState>,
) {
    init {
        require(contractVersion == DEVICE_SYNC_CONTRACT_VERSION)
        require(generatedAtEpochMillis >= 0L)
        require(deviceId.isNotBlank())
        require(sources.map { it.identity.syncSourceId }.distinct().size == sources.size)
        require(channels.map(SyncChannelState::key).distinct().size == channels.size)
        require(groups.map(SyncGroupState::key).distinct().size == groups.size)
        require(memberships.map(SyncGroupMembershipState::key).distinct().size == memberships.size)
    }
}
