package app.ownplay.player.live

import app.ownplay.player.persistence.ChannelAvailability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

data class LiveBrowseState(
    val categories: List<LiveCategory> = emptyList(),
    val customGroups: List<LiveCustomGroup> = emptyList(),
    val channels: List<LiveChannelItem> = emptyList(),
    val categoryNavigationChannels: List<LiveChannelItem> = emptyList(),
    val channelCategoryKeyById: Map<String, String?> = emptyMap(),
    val catalogChannelCount: Int = 0,
    val query: LiveBrowseQuery = LiveBrowseQuery(),
)

class LiveBrowseSession(
    initialQuery: LiveBrowseQuery = LiveBrowseQuery(),
) {
    private val query = MutableStateFlow(initialQuery)

    fun observe(catalog: Flow<LiveCatalogSnapshot>): Flow<LiveBrowseState> = combine(
        catalog,
        query,
    ) { snapshot, currentQuery ->
        val visibleCategories = snapshot.categories
            .asSequence()
            .filter { category -> currentQuery.includeHidden || !category.isHidden }
            .sortedWith(
                compareBy<LiveCategory> { it.manualOrder == null }
                    .thenBy { it.manualOrder ?: Long.MAX_VALUE }
                    .thenBy(LiveCategory::providerOrder),
            )
            .toList()
        LiveBrowseState(
            categories = visibleCategories,
            customGroups = snapshot.customGroups.sortedBy(LiveCustomGroup::groupOrder),
            channels = LiveBrowseProjector.project(
                records = snapshot.channels,
                query = currentQuery,
                customGroupIdsByChannelId = snapshot.customGroupIdsByChannelId,
                hiddenCategoryKeys = snapshot.hiddenCategoryKeys,
            ),
            categoryNavigationChannels = LiveBrowseProjector.project(
                records = snapshot.channels,
                query = currentQuery.copy(
                    searchTerm = "",
                    categoryKey = null,
                ),
                customGroupIdsByChannelId = snapshot.customGroupIdsByChannelId,
                hiddenCategoryKeys = snapshot.hiddenCategoryKeys,
            ),
            channelCategoryKeyById = snapshot.channels.associate { channel ->
                channel.channelId to channel.providerCategoryKey
            },
            catalogChannelCount = snapshot.channels.count { channel ->
                channel.availability != ChannelAvailability.REMOVED
            },
            query = currentQuery,
        )
    }

    fun updateSearch(searchTerm: String) {
        query.update { current -> current.copy(searchTerm = searchTerm) }
    }

    fun selectCategory(categoryKey: String?) {
        query.update { current -> current.copy(categoryKey = categoryKey) }
    }

    fun selectCustomGroup(groupId: String?) {
        query.update { current -> current.copy(customGroupId = groupId) }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        query.update { current -> current.copy(favoritesOnly = enabled) }
    }

    fun setHiddenOnly(
        enabled: Boolean,
        includeHiddenWhenDisabled: Boolean = false,
    ) {
        query.update { current ->
            current.copy(
                hiddenOnly = enabled,
                includeHidden = enabled || includeHiddenWhenDisabled,
            )
        }
    }

    fun setOrder(order: LiveBrowseOrder) {
        query.update { current -> current.copy(order = order) }
    }

    fun setIncludeHidden(enabled: Boolean) {
        query.update { current ->
            current.copy(
                includeHidden = enabled,
                hiddenOnly = if (enabled) current.hiddenOnly else false,
            )
        }
    }

    fun setIncludeRemoved(enabled: Boolean) {
        query.update { current -> current.copy(includeRemoved = enabled) }
    }
}
