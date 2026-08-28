package app.ownplay.player.live.ingest

import app.ownplay.player.persistence.reconcile.ProviderIdentity
import app.ownplay.player.source.m3u.M3uEntry
import app.ownplay.player.source.m3u.M3uPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialLiveCatalogM3uIdentityTest {
    @Test
    fun duplicateTvgIdVariantsReceiveUniqueDeterministicProviderKeys() {
        val hd = M3uEntry(
            displayName = "News HD",
            streamUrl = "https://example.test/live/news-hd.m3u8?token=one",
            tvgId = "NEWS.ONE",
            groupTitle = "News",
        )
        val sd = hd.copy(
            displayName = "News SD",
            streamUrl = "https://example.test/live/news-sd.m3u8?token=two",
        )

        val catalog = InitialLiveCatalogFactory.fromM3u(M3uPlaylist(entries = listOf(hd, sd)))

        assertEquals(2, catalog.channels.size)
        assertEquals(2, catalog.channels.map { it.providerKey }.toSet().size)
        assertTrue(catalog.channels.any { it.providerKey == ProviderIdentity.m3u(hd) })
    }

    @Test
    fun duplicateVariantIdentityIsStableAcrossPlaylistOrdering() {
        val first = M3uEntry(
            displayName = "News HD",
            streamUrl = "https://example.test/live/news-hd.m3u8?token=one",
            tvgId = "NEWS.ONE",
        )
        val second = first.copy(
            displayName = "News SD",
            streamUrl = "https://example.test/live/news-sd.m3u8?token=two",
        )

        val normalOrder = InitialLiveCatalogFactory.fromM3u(
            M3uPlaylist(entries = listOf(first, second)),
        ).channels.associate { it.providerName to it.providerKey }
        val reversedOrder = InitialLiveCatalogFactory.fromM3u(
            M3uPlaylist(entries = listOf(second, first)),
        ).channels.associate { it.providerName to it.providerKey }

        assertEquals(normalOrder, reversedOrder)
        assertNotEquals(normalOrder.getValue("News HD"), normalOrder.getValue("News SD"))
    }

    @Test
    fun exactDuplicateEntryIsCollapsedInsteadOfRejectingWholeCatalog() {
        val entry = M3uEntry(
            displayName = "News",
            streamUrl = "https://example.test/live/news.m3u8",
            tvgId = "NEWS.ONE",
        )

        val catalog = InitialLiveCatalogFactory.fromM3u(
            M3uPlaylist(entries = listOf(entry, entry)),
        )

        assertEquals(1, catalog.channels.size)
        assertEquals(ProviderIdentity.m3u(entry), catalog.channels.single().providerKey)
    }

    @Test
    fun nonCollisionProviderIdentityRemainsBackwardCompatible() {
        val entry = M3uEntry(
            displayName = "Sports",
            streamUrl = "https://example.test/live/sports.m3u8",
            tvgId = "SPORTS.ONE",
        )

        val channel = InitialLiveCatalogFactory.fromM3u(
            M3uPlaylist(entries = listOf(entry)),
        ).channels.single()

        assertEquals(ProviderIdentity.m3u(entry), channel.providerKey)
    }
}
