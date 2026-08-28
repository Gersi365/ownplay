package app.ownplay.player.live

import app.ownplay.player.persistence.ChannelAvailability
import app.ownplay.player.persistence.live.LiveChannelRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveBrowseRemovedCategoryTest {
    @Test
    fun `normal browse hides categories backed only by removed channels`() = runBlocking {
        val snapshot = LiveCatalogSnapshot(
            categories = listOf(
                LiveCategory("active", "Active", 0),
                LiveCategory("stale", "Stale", 1),
            ),
            channels = listOf(
                record("active-channel", "active", ChannelAvailability.AVAILABLE),
                record("removed-channel", "stale", ChannelAvailability.REMOVED),
            ),
        )
        val session = LiveBrowseSession()

        val normal = session.observe(flowOf(snapshot)).first()
        assertEquals(listOf("active"), normal.categories.map { it.providerCategoryKey })
        assertEquals(listOf("active-channel"), normal.channels.map { it.channelId })

        session.setIncludeRemoved(true)
        val management = session.observe(flowOf(snapshot)).first()
        assertEquals(listOf("active", "stale"), management.categories.map { it.providerCategoryKey })
        assertEquals(
            listOf("active-channel", "removed-channel"),
            management.channels.map { it.channelId },
        )
    }

    private fun record(
        id: String,
        categoryKey: String,
        availability: String,
    ) = LiveChannelRecord(
        channelId = id,
        sourceId = "source",
        providerCategoryKey = categoryKey,
        categoryName = categoryKey,
        providerName = id,
        tvgName = null,
        logoRef = null,
        providerOrder = if (availability == ChannelAvailability.AVAILABLE) 0 else 1,
        availability = availability,
        localDisplayName = null,
        logoOverrideRef = null,
        manualOrder = null,
        favoriteOrder = null,
        hiddenAtEpochMillis = null,
        recentAtEpochMillis = null,
    )
}
