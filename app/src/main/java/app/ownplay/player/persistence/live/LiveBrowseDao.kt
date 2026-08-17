package app.ownplay.player.persistence.live

import androidx.room.Dao
import androidx.room.Query
import app.ownplay.player.persistence.ProviderCategoryEntity
import kotlinx.coroutines.flow.Flow

data class LiveChannelRecord(
    val channelId: String,
    val sourceId: String,
    val providerCategoryKey: String?,
    val categoryName: String?,
    val providerName: String,
    val tvgName: String?,
    val logoRef: String?,
    val providerOrder: Long,
    val availability: String,
    val localDisplayName: String?,
    val logoOverrideRef: String?,
    val manualOrder: Long?,
    val favoriteOrder: Long?,
    val hiddenAtEpochMillis: Long?,
)

@Dao
interface LiveBrowseDao {
    @Query(
        """
        SELECT
            channel.channelId,
            channel.sourceId,
            channel.providerCategoryKey,
            category.name AS categoryName,
            channel.providerName,
            channel.tvgName,
            channel.logoRef,
            channel.providerOrder,
            channel.availability,
            customization.localDisplayName,
            customization.logoOverrideRef,
            customization.manualOrder,
            favorite.favoriteOrder,
            hidden.hiddenAtEpochMillis
        FROM provider_channels AS channel
        LEFT JOIN provider_categories AS category
            ON category.sourceId = channel.sourceId
            AND category.providerCategoryKey = channel.providerCategoryKey
        LEFT JOIN channel_customizations AS customization
            ON customization.channelId = channel.channelId
        LEFT JOIN favorite_entries AS favorite
            ON favorite.channelId = channel.channelId
        LEFT JOIN hidden_entries AS hidden
            ON hidden.channelId = channel.channelId
        WHERE channel.sourceId = :sourceId
        ORDER BY channel.providerOrder ASC
        """,
    )
    fun observeChannels(sourceId: String): Flow<List<LiveChannelRecord>>

    @Query(
        "SELECT * FROM provider_categories " +
            "WHERE sourceId = :sourceId ORDER BY providerOrder ASC",
    )
    fun observeCategories(sourceId: String): Flow<List<ProviderCategoryEntity>>
}
