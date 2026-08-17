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
    suspend fun upsertCustomizations(customizations: List<ChannelCustomizationEntity>)

    @Query(
        """
        SELECT channel.channelId
        FROM provider_channels AS channel
        LEFT JOIN channel_customizations AS customization
            ON customization.channelId = channel.channelId
        WHERE channel.sourceId = :sourceId
        ORDER BY
            customization.manualOrder IS NULL ASC,
            customization.manualOrder ASC,
            channel.providerOrder ASC,
            channel.channelId ASC
        """,
    )
    suspend fun resolvedChannelOrder(sourceId: String): List<String>

    @Query(
        """
        SELECT customization.*
        FROM channel_customizations AS customization
        INNER JOIN provider_channels AS channel
            ON channel.channelId = customization.channelId
        WHERE channel.sourceId = :sourceId
        """,
    )
    suspend fun customizationsForSource(sourceId: String): List<ChannelCustomizationEntity>

    @Upsert
    suspend fun upsertHidden(entry: HiddenEntryEntity)

    @Upsert
    suspend fun upsertHidden(entries: List<HiddenEntryEntity>)

    @Query("DELETE FROM hidden_entries WHERE channelId = :channelId")
    suspend fun unhide(channelId: String)

    @Upsert
    suspend fun upsertFavorite(entry: FavoriteEntryEntity)

    @Upsert
    suspend fun upsertFavorites(entries: List<FavoriteEntryEntity>)

    @Query(
        """
        SELECT favorite.*
        FROM favorite_entries AS favorite
        INNER JOIN provider_channels AS channel
            ON channel.channelId = favorite.channelId
        WHERE channel.sourceId = :sourceId
        ORDER BY favorite.favoriteOrder ASC, favorite.addedAtEpochMillis ASC, favorite.channelId ASC
        """,
    )
    suspend fun favoriteEntriesForSource(sourceId: String): List<FavoriteEntryEntity>

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
