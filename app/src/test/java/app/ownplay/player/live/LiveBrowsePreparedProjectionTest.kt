package app.ownplay.player.live

import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.live.LiveChannelRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBrowsePreparedProjectionTest {
    @Test
    fun preparedProjectionMatchesDirectProjectionAcrossQueryModes() {
        val records = listOf(
            record(
                id = "local-news",
                providerName = "Provider News",
                localDisplayName = "My News",
                categoryKey = "news",
                categoryName = "News",
                providerOrder = 3,
                manualOrder = 0,
                favoriteOrder = 1,
            ),
            record(
                id = "sports",
                providerName = "Sports Plus",
                categoryKey = "sports",
                categoryName = "Sports",
                providerOrder = 1,
                favoriteOrder = 0,
            ),
            record(
                id = "hidden",
                providerName = "Hidden News",
                categoryKey = "news",
                categoryName = "News",
                providerOrder = 2,
                hiddenAtEpochMillis = 10,
            ),
            record(
                id = "removed",
                providerName = "Removed News",
                categoryKey = "news",
                categoryName = "News",
                providerOrder = 4,
                availability = ChannelAvailability.REMOVED,
            ),
        )
        val memberships = mapOf(
            "local-news" to setOf("group-a"),
            "sports" to setOf("group-b"),
        )
        val hiddenCategories = setOf("news")
        val prepared = LiveBrowseProjector.prepare(
            records = records,
            customGroupIdsByChannelId = memberships,
            hiddenCategoryKeys = hiddenCategories,
        )
        val queries = listOf(
            LiveBrowseQuery(),
            LiveBrowseQuery(includeHidden = true),
            LiveBrowseQuery(includeHidden = true, includeRemoved = true),
            LiveBrowseQuery(includeHidden = true, searchTerm = "provider news"),
            LiveBrowseQuery(includeHidden = true, categoryKey = "news", order = LiveBrowseOrder.MY_ORDER),
            LiveBrowseQuery(customGroupId = "group-b"),
            LiveBrowseQuery(favoritesOnly = true, includeHidden = true, order = LiveBrowseOrder.FAVORITE_ORDER),
            LiveBrowseQuery(includeHidden = true, order = LiveBrowseOrder.A_TO_Z),
            LiveBrowseQuery(includeHidden = true, order = LiveBrowseOrder.Z_TO_A),
            LiveBrowseQuery(includeHidden = true, order = LiveBrowseOrder.CATEGORY),
        )

        queries.forEach { query ->
            assertEquals(
                LiveBrowseProjector.project(
                    records = records,
                    query = query,
                    customGroupIdsByChannelId = memberships,
                    hiddenCategoryKeys = hiddenCategories,
                ),
                LiveBrowseProjector.projectPrepared(prepared, query),
            )
        }
    }

    @Test
    fun preparationStoresNormalizedSearchAndSortKeysOncePerCatalogRecord() {
        val prepared = LiveBrowseProjector.prepare(
            records = listOf(
                record(
                    id = "one",
                    providerName = "PrOvIdEr NaMe",
                    localDisplayName = "My Näme",
                    categoryKey = "news",
                    categoryName = "NeWs",
                    providerOrder = 1,
                ),
            ),
        ).single()

        assertEquals("my näme", prepared.normalizedDisplayName)
        assertEquals("provider name", prepared.normalizedProviderName)
        assertEquals("news", prepared.normalizedCategoryName)
    }

    @Test
    fun preparedProjectionKeepsLargeCatalogFilteringDeterministic() {
        val records = (0 until 5_000).map { index ->
            record(
                id = "channel-$index",
                providerName = if (index % 1_000 == 0) "Needle $index" else "Channel $index",
                categoryKey = if (index % 2 == 0) "even" else "odd",
                categoryName = if (index % 2 == 0) "Even" else "Odd",
                providerOrder = (5_000 - index).toLong(),
            )
        }
        val prepared = LiveBrowseProjector.prepare(records)

        val result = LiveBrowseProjector.projectPrepared(
            prepared = prepared,
            query = LiveBrowseQuery(
                searchTerm = "needle",
                order = LiveBrowseOrder.A_TO_Z,
            ),
        )

        assertEquals(
            listOf("channel-0", "channel-1000", "channel-2000", "channel-3000", "channel-4000"),
            result.map(LiveChannelItem::channelId),
        )
    }

    private fun record(
        id: String,
        providerName: String,
        localDisplayName: String? = null,
        categoryKey: String? = null,
        categoryName: String? = null,
        providerOrder: Long,
        manualOrder: Long? = null,
        favoriteOrder: Long? = null,
        hiddenAtEpochMillis: Long? = null,
        availability: String = ChannelAvailability.AVAILABLE,
    ) = LiveChannelRecord(
        channelId = id,
        sourceId = "source",
        providerCategoryKey = categoryKey,
        categoryName = categoryName,
        providerName = providerName,
        tvgName = null,
        logoRef = null,
        providerOrder = providerOrder,
        availability = availability,
        localDisplayName = localDisplayName,
        logoOverrideRef = null,
        manualOrder = manualOrder,
        favoriteOrder = favoriteOrder,
        hiddenAtEpochMillis = hiddenAtEpochMillis,
        recentAtEpochMillis = null,
    )
}
