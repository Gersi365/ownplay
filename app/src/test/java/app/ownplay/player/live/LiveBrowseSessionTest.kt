package app.ownplay.player.live

import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.live.LiveChannelRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveBrowseSessionTest {
    @Test
    fun sessionProjectsCatalogUsingCurrentQueryState() = runBlocking {
        val session = LiveBrowseSession()
        session.updateSearch("news")
        session.selectCategory("news")
        session.setOrder(LiveBrowseOrder.MY_ORDER)

        val state = session.observe(flowOf(snapshot())).first()

        assertEquals("news", state.query.searchTerm)
        assertEquals("news", state.query.categoryKey)
        assertEquals(LiveBrowseOrder.MY_ORDER, state.query.order)
        assertEquals(listOf("news-two", "news-one"), state.channels.map { it.channelId })
        assertEquals(listOf("news", "sports"), state.categories.map { it.providerCategoryKey })
    }

    @Test
    fun leavingHiddenManagementRestoresNormalHiddenExclusion() = runBlocking {
        val session = LiveBrowseSession()
        session.setHiddenOnly(true)

        val hiddenState = session.observe(flowOf(snapshot())).first()
        assertTrue(hiddenState.query.hiddenOnly)
        assertTrue(hiddenState.query.includeHidden)
        assertEquals(listOf("hidden"), hiddenState.channels.map { it.channelId })

        session.setHiddenOnly(false)
        val normalState = session.observe(flowOf(snapshot())).first()

        assertFalse(normalState.query.hiddenOnly)
        assertFalse(normalState.query.includeHidden)
        assertFalse(normalState.channels.any { it.channelId == "hidden" })
    }

    @Test
    fun favoritesAndVisibilityFlagsRemainExplicitTransientState() = runBlocking {
        val session = LiveBrowseSession()
        session.setFavoritesOnly(true)
        session.setIncludeHidden(true)
        session.setIncludeRemoved(false)

        val state = session.observe(flowOf(snapshot())).first()

        assertTrue(state.query.favoritesOnly)
        assertTrue(state.query.includeHidden)
        assertFalse(state.query.includeRemoved)
        assertEquals(listOf("news-two"), state.channels.map { it.channelId })
    }

    private fun snapshot() = LiveCatalogSnapshot(
        categories = listOf(
            LiveCategory("sports", "Sports", 2),
            LiveCategory("news", "News", 1),
        ),
        channels = listOf(
            record(
                id = "news-one",
                name = "News One",
                category = "news",
                providerOrder = 2,
                manualOrder = 2,
            ),
            record(
                id = "news-two",
                name = "News Two",
                category = "news",
                providerOrder = 3,
                manualOrder = 1,
                favoriteOrder = 0,
            ),
            record(
                id = "sports",
                name = "Sports",
                category = "sports",
                providerOrder = 1,
            ),
            record(
                id = "hidden",
                name = "Hidden News",
                category = "news",
                providerOrder = 4,
                hiddenAt = 10,
            ),
            record(
                id = "removed",
                name = "Removed News",
                category = "news",
                providerOrder = 5,
                availability = ChannelAvailability.REMOVED,
            ),
        ),
    )

    private fun record(
        id: String,
        name: String,
        category: String,
        providerOrder: Long,
        manualOrder: Long? = null,
        favoriteOrder: Long? = null,
        hiddenAt: Long? = null,
        availability: String = ChannelAvailability.AVAILABLE,
    ) = LiveChannelRecord(
        channelId = id,
        sourceId = "source",
        providerCategoryKey = category,
        categoryName = category.replaceFirstChar(Char::uppercase),
        providerName = name,
        tvgName = null,
        logoRef = null,
        providerOrder = providerOrder,
        availability = availability,
        localDisplayName = null,
        logoOverrideRef = null,
        manualOrder = manualOrder,
        favoriteOrder = favoriteOrder,
        hiddenAtEpochMillis = hiddenAt,
        recentAtEpochMillis = null,
    )
}
