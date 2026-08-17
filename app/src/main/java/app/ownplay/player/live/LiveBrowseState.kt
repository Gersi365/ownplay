package app.ownplay.player.live

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

data class LiveBrowseState(
    val categories: List<LiveCategory> = emptyList(),
    val channels: List<LiveChannelItem> = emptyList(),
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
        LiveBrowseState(
            categories = snapshot.categories.sortedBy(LiveCategory::providerOrder),
            channels = LiveBrowseProjector.project(snapshot.channels, currentQuery),
            query = currentQuery,
        )
    }

    fun updateSearch(searchTerm: String) {
        query.update { current -> current.copy(searchTerm = searchTerm) }
    }

    fun selectCategory(categoryKey: String?) {
        query.update { current -> current.copy(categoryKey = categoryKey) }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        query.update { current -> current.copy(favoritesOnly = enabled) }
    }

    fun setOrder(order: LiveBrowseOrder) {
        query.update { current -> current.copy(order = order) }
    }

    fun setIncludeHidden(enabled: Boolean) {
        query.update { current -> current.copy(includeHidden = enabled) }
    }

    fun setIncludeRemoved(enabled: Boolean) {
        query.update { current -> current.copy(includeRemoved = enabled) }
    }
}
