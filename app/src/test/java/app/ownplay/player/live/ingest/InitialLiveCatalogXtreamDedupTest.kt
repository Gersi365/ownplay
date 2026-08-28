package app.ownplay.player.live.ingest

import app.ownplay.player.persistence.reconcile.ProviderIdentity
import app.ownplay.player.source.xtream.XtreamCategory
import app.ownplay.player.source.xtream.XtreamLiveStream
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialLiveCatalogXtreamDedupTest {
    @Test
    fun duplicateProviderRowsAreCollapsedByAuthoritativeIds() {
        val categories = listOf(
            XtreamCategory(id = "10", name = "News", parentId = null),
            XtreamCategory(id = "10", name = "News duplicate", parentId = null),
        )
        val streams = listOf(
            stream(streamId = 101, name = "News HD"),
            stream(streamId = 101, name = "News duplicate"),
        )

        val catalog = InitialLiveCatalogFactory.fromXtream(categories, streams)

        assertEquals(1, catalog.categories.size)
        assertEquals("News", catalog.categories.single().name)
        assertEquals(1, catalog.channels.size)
        assertEquals("News HD", catalog.channels.single().providerName)
        assertEquals(
            ProviderIdentity.xtreamLiveStream(101),
            catalog.channels.single().providerKey,
        )
    }

    @Test
    fun nonPositiveProviderStreamIdsAreSkipped() {
        val catalog = InitialLiveCatalogFactory.fromXtream(
            categories = listOf(XtreamCategory(id = "10", name = "News", parentId = null)),
            streams = listOf(
                stream(streamId = 0, name = "Invalid zero"),
                stream(streamId = -1, name = "Invalid negative"),
                stream(streamId = 101, name = "Valid"),
            ),
        )

        assertEquals(1, catalog.channels.size)
        assertEquals(101, catalog.channels.single().providerStreamId?.toInt())
    }

    private fun stream(streamId: Int, name: String): XtreamLiveStream = XtreamLiveStream(
        streamId = streamId,
        name = name,
        categoryId = "10",
        iconUrl = null,
        epgChannelId = null,
        archiveDurationDays = null,
        directSource = null,
    )
}
