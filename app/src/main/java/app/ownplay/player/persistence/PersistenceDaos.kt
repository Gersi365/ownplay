package app.ownplay.player.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistSourceDao {
    @Upsert
    suspend fun upsert(source: PlaylistSourceEntity)

    @Query("SELECT * FROM playlist_sources ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<PlaylistSourceEntity>>

    @Query("SELECT * FROM playlist_sources WHERE sourceId = :sourceId LIMIT 1")
    suspend fun getById(sourceId: String): PlaylistSourceEntity?
}

@Dao
interface ProviderCatalogDao {
    @Upsert
    suspend fun upsertCategories(categories: List<ProviderCategoryEntity>)

    @Upsert
    suspend fun upsertChannels(channels: List<ProviderChannelEntity>)

    @Query(
        "SELECT * FROM provider_channels " +
            "WHERE sourceId = :sourceId ORDER BY providerOrder ASC",
    )
    suspend fun channelsForSource(sourceId: String): List<ProviderChannelEntity>

    @Query(
        "SELECT * FROM provider_channels " +
            "WHERE sourceId = :sourceId AND providerKey = :providerKey LIMIT 1",
    )
    suspend fun channelByProviderKey(
        sourceId: String,
        providerKey: String,
    ): ProviderChannelEntity?
}

@Dao
interface PersonalizationDao {
    @Upsert
    suspend fun upsertCustomization(customization: ChannelCustomizationEntity)

    @Upsert
    suspend fun upsertHidden(entry: HiddenEntryEntity)

    @Query("DELETE FROM hidden_entries WHERE channelId = :channelId")
    suspend fun unhide(channelId: String)

    @Upsert
    suspend fun upsertFavorite(entry: FavoriteEntryEntity)

    @Query("DELETE FROM favorite_entries WHERE channelId = :channelId")
    suspend fun removeFavorite(channelId: String)

    @Upsert
    suspend fun upsertGroup(group: CustomGroupEntity)

    @Upsert
    suspend fun upsertGroupMembership(membership: CustomGroupMembershipEntity)

    @Query("DELETE FROM custom_group_memberships WHERE groupId = :groupId AND channelId = :channelId")
    suspend fun removeGroupMembership(
        groupId: String,
        channelId: String,
    )

    @Query("SELECT * FROM favorite_entries ORDER BY favoriteOrder ASC")
    fun observeFavorites(): Flow<List<FavoriteEntryEntity>>

    @Query("SELECT * FROM custom_groups ORDER BY groupOrder ASC")
    fun observeGroups(): Flow<List<CustomGroupEntity>>
}

@Dao
interface RefreshStateDao {
    @Upsert
    suspend fun upsert(state: PlaylistRefreshStateEntity)

    @Query("SELECT * FROM playlist_refresh_state WHERE sourceId = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): PlaylistRefreshStateEntity?
}
