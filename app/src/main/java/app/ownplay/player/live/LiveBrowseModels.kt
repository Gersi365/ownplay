package app.ownplay.player.live

import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.live.LiveChannelRecord
import java.util.Locale

data class LiveCategory(
    val providerCategoryKey: String,
    val name: String,
    val providerOrder: Long,
    val manualOrder: Long? = null,
    val isHidden: Boolean = false,
)

data class LiveChannelItem(
    val channelId: String,
    val sourceId: String,
    val categoryKey: String?,
    val categoryName: String?,
    val providerName: String,
    val localDisplayName: String?,
    val displayName: String,
    val logoRef: String?,
    val hasLogoOverride: Boolean,
    val providerOrder: Long,
    val manualOrder: Long?,
    val favoriteOrder: Long?,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val availability: String,
    val recentAtEpochMillis: Long?,
    val customGroupIds: Set<String> = emptySet(),
)

enum class LiveBrowseOrder {
    PROVIDER,
    MY_ORDER,
    FAVORITE_ORDER,
    RECENTLY_WATCHED,
    A_TO_Z,
    Z_TO_A,
    CATEGORY,
}

data class LiveBrowseQuery(
    val searchTerm: String = "",
    val categoryKey: String? = null,
    val customGroupId: String? = null,
    val favoritesOnly: Boolean = false,
    val hiddenOnly: Boolean = false,
    val includeHidden: Boolean = false,
    val includeRemoved: Boolean = false,
    val order: LiveBrowseOrder = LiveBrowseOrder.PROVIDER,
)

object LiveBrowseProjector {
    fun project(
        records: List<LiveChannelRecord>,
        query: LiveBrowseQuery,
        customGroupIdsByChannelId: Map<String, Set<String>> = emptyMap(),
        hiddenCategoryKeys: Set<String> = emptySet(),
    ): List<LiveChannelItem> {
        val normalizedSearch = query.searchTerm.trim().lowercase(Locale.ROOT)

        val filtered = records.asSequence()
            .map { record ->
                toItem(
                    record = record,
                    customGroupIds = customGroupIdsByChannelId[record.channelId].orEmpty(),
                    hiddenCategoryKeys = hiddenCategoryKeys,
                )
            }
            .filter { item ->
                if (query.hiddenOnly) item.isHidden else query.includeHidden || !item.isHidden
            }
            .filter { item -> query.includeRemoved || item.availability != ChannelAvailability.REMOVED }
            .filter { item -> query.categoryKey == null || item.categoryKey == query.categoryKey }
            .filter { item -> query.customGroupId == null || query.customGroupId in item.customGroupIds }
            .filter { item -> !query.favoritesOnly || item.isFavorite }
            .filter { item ->
                normalizedSearch.isEmpty() ||
                    item.displayName.lowercase(Locale.ROOT).contains(normalizedSearch) ||
                    item.categoryName.orEmpty().lowercase(Locale.ROOT).contains(normalizedSearch)
            }
            .toList()

        return when (query.order) {
            LiveBrowseOrder.PROVIDER -> filtered.sortedBy(LiveChannelItem::providerOrder)
            LiveBrowseOrder.MY_ORDER -> filtered.sortedWith(
                compareBy<LiveChannelItem> { it.manualOrder == null }
                    .thenBy { it.manualOrder ?: Long.MAX_VALUE }
                    .thenBy(LiveChannelItem::providerOrder),
            )
            LiveBrowseOrder.FAVORITE_ORDER -> filtered.sortedWith(
                compareBy<LiveChannelItem> { it.favoriteOrder == null }
                    .thenBy { it.favoriteOrder ?: Long.MAX_VALUE }
                    .thenBy(LiveChannelItem::providerOrder),
            )
            LiveBrowseOrder.RECENTLY_WATCHED -> filtered.sortedWith(
                compareBy<LiveChannelItem> { it.recentAtEpochMillis == null }
                    .thenByDescending { it.recentAtEpochMillis ?: Long.MIN_VALUE }
                    .thenBy(LiveChannelItem::providerOrder),
            )
            LiveBrowseOrder.A_TO_Z -> filtered.sortedWith(
                compareBy<LiveChannelItem> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy(LiveChannelItem::providerOrder),
            )
            LiveBrowseOrder.Z_TO_A -> filtered.sortedWith(
                compareByDescending<LiveChannelItem> { it.displayName.lowercase(Locale.ROOT) }
                    .thenBy(LiveChannelItem::providerOrder),
            )
            LiveBrowseOrder.CATEGORY -> filtered.sortedWith(
                compareBy<LiveChannelItem> { it.categoryName.orEmpty().lowercase(Locale.ROOT) }
                    .thenBy(LiveChannelItem::providerOrder),
            )
        }
    }

    private fun toItem(
        record: LiveChannelRecord,
        customGroupIds: Set<String>,
        hiddenCategoryKeys: Set<String>,
    ): LiveChannelItem = LiveChannelItem(
        channelId = record.channelId,
        sourceId = record.sourceId,
        categoryKey = record.providerCategoryKey,
        categoryName = record.categoryName,
        providerName = record.providerName,
        localDisplayName = record.localDisplayName,
        displayName = record.localDisplayName?.takeIf(String::isNotBlank)
            ?: record.tvgName?.takeIf(String::isNotBlank)
            ?: record.providerName,
        logoRef = record.logoOverrideRef ?: record.logoRef,
        hasLogoOverride = record.logoOverrideRef != null,
        providerOrder = record.providerOrder,
        manualOrder = record.manualOrder,
        favoriteOrder = record.favoriteOrder,
        isFavorite = record.favoriteOrder != null,
        isHidden = record.hiddenAtEpochMillis != null ||
            record.providerCategoryKey in hiddenCategoryKeys,
        availability = record.availability,
        recentAtEpochMillis = record.recentAtEpochMillis,
        customGroupIds = customGroupIds,
    )
}
