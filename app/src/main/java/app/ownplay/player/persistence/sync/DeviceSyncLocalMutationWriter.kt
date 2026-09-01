package app.ownplay.player.persistence.sync

import app.ownplay.player.persistence.CustomGroupEntity
import app.ownplay.player.persistence.CustomGroupMembershipEntity
import app.ownplay.player.persistence.FavoriteEntryEntity
import app.ownplay.player.persistence.OwnPlayDatabase

/**
 * Retired Device Sync compatibility shim.
 *
 * Cross-device Device Sync / pairing is no longer an OwnPlay product feature. Room v6 still
 * contains the historical device_sync_* tables so existing databases can be opened without an
 * unrelated destructive/schema migration. Normal OwnPlay mutations must not populate or advance
 * that retired metadata.
 *
 * This class intentionally preserves the old call surface as no-ops while the v6 schema is kept
 * for compatibility. It performs no reads or writes against DeviceSyncDao and starts no sync,
 * pairing, transport, or crypto work.
 */
@Suppress("UNUSED_PARAMETER")
class DeviceSyncLocalMutationWriter(
    database: OwnPlayDatabase,
) {
    suspend fun recordSourceCreatedOrRestored(localSourceId: String) = Unit

    suspend fun recordSourceRenamed(localSourceId: String, displayName: String) = Unit

    suspend fun recordEncryptedSecretRef(syncSourceId: String, encryptedSecretRef: String?) = Unit

    suspend fun recordSourceDeleted(localSourceId: String) = Unit

    suspend fun recordLocalDisplayName(
        sourceId: String,
        channelId: String,
        localDisplayName: String?,
    ) = Unit

    suspend fun recordHidden(
        sourceId: String,
        channelIds: Collection<String>,
        hidden: Boolean,
    ) = Unit

    suspend fun recordFavorites(
        sourceId: String,
        activeEntries: List<FavoriteEntryEntity>,
        removedChannelIds: Collection<String> = emptyList(),
    ) = Unit

    suspend fun recordManualOrder(
        sourceId: String,
        assignments: Map<String, Long>,
    ) = Unit

    suspend fun recordGroupCreated(group: CustomGroupEntity) = Unit

    suspend fun recordGroupRenamed(group: CustomGroupEntity) = Unit

    suspend fun recordGroupDeleted(group: CustomGroupEntity) = Unit

    suspend fun recordMemberships(
        groupId: String,
        activeMemberships: List<CustomGroupMembershipEntity>,
        removedChannelIds: Collection<String> = emptyList(),
    ) = Unit
}
