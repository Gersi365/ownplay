package app.ownplay.player.live

import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.live.LiveChannelRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBrowseProjectorTest {
    private val records = listOf(
        record(
            id = "one",
            providerName = "Provider One",
            tvgName = "News One",
            categoryKey = "news",
            categoryName = "News",
            providerOrder = 2,
            manualOrder = 1,
        ),
        record(
            id = "two",
            providerName = "Sports Two",
            categoryKey = "sports",
            categoryName = "Sports",
            providerOrder = 1,
            favoriteOrder = 0,
        ),
        record(
            id = "hidden",
            providerName = "Hidden Channel",
            categoryKey = "news",
            categoryName = "News",
            providerOrder = 3,
            hiddenAtEpochMillis = 10,
        ),
        record(
            id = "removed",
            providerName = "Removed Channel",
            categoryKey = "news",
            categoryName = "News",
            providerOrder = 4,
            availability = ChannelAvailability.REMOVED,
        ),
    )

    @Test
    fun defaultProjectionExcludesHiddenAndRemovedAndUsesProviderOrder() {
        val result = LiveBrowseProjector.project(records, LiveBrowseQuery())

        assertEquals(listOf("two", "one"), result.map { it.channelId })
        assertFalse(result.any { it.isHidden })
        assertFalse(result.any { it.availability == ChannelAvailability.REMOVED })
    }

    @Test
    fun localAndTvgNamesOverrideProviderName() {
        val customized = records.first().copy(localDisplayName = "My News")
        val result = LiveBrowseProjector.project(listOf(customized), LiveBrowseQuery())

        assertEquals("My News", result.single().displayName)
    }

    @Test
    fun searchAndCategoryFilteringDoNotMutatePersistentOrdering() {
        val query = LiveBrowseQuery(
            searchTerm = "news",
            categoryKey = "news",
            order = LiveBrowseOrder.MY_ORDER,
        )

        val result = LiveBrowseProjector.project(records, query)

        assertEquals(listOf("one"), result.map { it.channelId })
        assertEquals(1L, result.single().manualOrder)
    }

    @Test
    fun favoritesCanUseTheirOwnPersistentOrder() {
        val extraFavorite = records.first().copy(favoriteOrder = 5)
        val query = LiveBrowseQuery(
            favoritesOnly = true,
            order = LiveBrowseOrder.FAVORITE_ORDER,
        )

        val result = LiveBrowseProjector.project(
            records = listOf(extraFavorite, records[1]),
            query = query,
        )

        assertEquals(listOf("two", "one"), result.map { it.channelId })
        assertTrue(result.all { it.isFavorite })
    }

    @Test
    fun myOrderKeepsUnorderedNewChannelsAfterEstablishedManualOrder() {
        val result = LiveBrowseProjector.project(
            records = records.take(2),
            query = LiveBrowseQuery(order = LiveBrowseOrder.MY_ORDER),
        )

        assertEquals(listOf("one", "two"), result.map { it.channelId })
    }

    private fun record(
        id: String,
        providerName: String,
        tvgName: String? = null,
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
        tvgName = tvgName,
        logoRef = null,
        providerOrder = providerOrder,
        availability = availability,
        localDisplayName = null,
        logoOverrideRef = null,
        manualOrder = manualOrder,
        favoriteOrder = favoriteOrder,
        hiddenAtEpochMillis = hiddenAtEpochMillis,
    )
}
