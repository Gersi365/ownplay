package app.ownplay.player.live

import app.ownplay.player.persistence.live.LiveBrowseDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LiveCatalogSnapshot(
    val categories: List<LiveCategory>,
    val channels: List<app.ownplay.player.persistence.live.LiveChannelRecord>,
)

class LiveCatalogRepository(
    private val dao: LiveBrowseDao,
) {
    fun observe(sourceId: String): Flow<LiveCatalogSnapshot> = combine(
        dao.observeCategories(sourceId),
        dao.observeChannels(sourceId),
    ) { categories, channels ->
        LiveCatalogSnapshot(
            categories = categories.map { category ->
                LiveCategory(
                    providerCategoryKey = category.providerCategoryKey,
                    name = category.name,
                    providerOrder = category.providerOrder,
                )
            },
            channels = channels,
        )
    }
}
