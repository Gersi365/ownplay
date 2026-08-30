package app.ownplay.player.persistence.sync

import app.ownplay.player.persistence.CustomGroupEntity
import app.ownplay.player.persistence.CustomGroupMembershipEntity
import app.ownplay.player.persistence.FavoriteEntryEntity
import app.ownplay.player.persistence.OwnPlayDatabase

/**
 * Compatibility shim for the deferred cross-device sync design.
 *
 * OwnPlay currently keeps playlist/source state and personalization local to each installation.
 * Database v6 sync tables remain in the schema so update-compatible installs do not require a
 * destructive downgrade, but local product mutations must not create or advance sync metadata.
 *
 * Keep this class as a no-op until cross-device sync is explicitly brought back into product scope.
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
