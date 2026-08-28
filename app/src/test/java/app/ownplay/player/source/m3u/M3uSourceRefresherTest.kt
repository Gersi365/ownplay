package app.ownplay.player.source.m3u

import app.ownplay.player.live.ingest.InitialLiveCatalogIngestResult
import app.ownplay.player.persistence.SourceKinds
import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import app.ownplay.player.source.onboarding.SourceOnboardingFailure
import app.ownplay.player.source.onboarding.SourceOnboardingResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uSourceRefresherTest {
    @Test
    fun `remote refresh preserves source cleartext opt in`() = runBlocking {
        var receivedUrl: String? = null
        var receivedCleartext = false
        val refresher = M3uSourceRefresher(
            loadRemote = { url, allowCleartext ->
                receivedUrl = url
                receivedCleartext = allowCleartext
                SourceResult.Success(playlist())
            },
            loadLocal = { error("local loader should not run") },
            ingest = { _, _, _ -> InitialLiveCatalogIngestResult.Success(1, 1) },
            nowEpochMillis = { 42L },
        )
        val locator = M3uSourceLocatorCodec.encode(
            M3uSourceLocator(
                value = "http://provider.test/list.m3u?token=secret",
                allowCleartext = true,
            ),
        )

        val result = refresher.refresh(
            M3uRefreshRequest(
                sourceId = "source",
                sourceKind = SourceKinds.REMOTE_M3U,
                storedLocator = locator,
            ),
        )

        assertEquals(SourceOnboardingResult.Success("source", 1), result)
        assertEquals("http://provider.test/list.m3u?token=secret", receivedUrl)
        assertTrue(receivedCleartext)
    }

    @Test
    fun `legacy remote locator remains cleartext disabled`() = runBlocking {
        var receivedCleartext = true
        val refresher = M3uSourceRefresher(
            loadRemote = { _, allowCleartext ->
                receivedCleartext = allowCleartext
                SourceResult.Success(playlist())
            },
            loadLocal = { error("local loader should not run") },
            ingest = { _, _, _ -> InitialLiveCatalogIngestResult.Success(1, 1) },
        )

        refresher.refresh(
            M3uRefreshRequest(
                sourceId = "source",
                sourceKind = SourceKinds.REMOTE_M3U,
                storedLocator = "https://provider.test/list.m3u",
            ),
        )

        assertFalse(receivedCleartext)
    }

    @Test
    fun `local refresh loads content uri and keeps generation`() = runBlocking {
        var receivedUri: String? = null
        var receivedGeneration: Long? = null
        val refresher = M3uSourceRefresher(
            loadRemote = { _, _ -> error("remote loader should not run") },
            loadLocal = { uri ->
                receivedUri = uri
                SourceResult.Success(playlist())
            },
            ingest = { _, generation, _ ->
                receivedGeneration = generation
                InitialLiveCatalogIngestResult.Success(1, 1)
            },
            nowEpochMillis = { 9_000L },
        )

        val result = refresher.refresh(
            M3uRefreshRequest(
                sourceId = "source",
                sourceKind = SourceKinds.LOCAL_M3U,
                storedLocator = "content://provider/document/playlist",
            ),
        )

        assertEquals(SourceOnboardingResult.Success("source", 1), result)
        assertEquals("content://provider/document/playlist", receivedUri)
        assertEquals(9_000L, receivedGeneration)
    }

    @Test
    fun `loader failure is preserved as source failure`() = runBlocking {
        val refresher = M3uSourceRefresher(
            loadRemote = { _, _ -> SourceResult.Failure(SourceError.NetworkUnavailable) },
            loadLocal = { error("local loader should not run") },
            ingest = { _, _, _ -> error("ingest should not run") },
        )

        val result = refresher.refresh(
            M3uRefreshRequest(
                sourceId = "source",
                sourceKind = SourceKinds.REMOTE_M3U,
                storedLocator = "https://provider.test/list.m3u",
            ),
        )

        assertEquals(
            SourceOnboardingResult.Failure(
                SourceOnboardingFailure.SourceFailure(SourceError.NetworkUnavailable),
            ),
            result,
        )
    }

    @Test
    fun `ingest failure does not report refresh success`() = runBlocking {
        val refresher = M3uSourceRefresher(
            loadRemote = { _, _ -> SourceResult.Success(playlist()) },
            loadLocal = { error("local loader should not run") },
            ingest = { _, _, _ -> InitialLiveCatalogIngestResult.PersistenceFailure },
        )

        val result = refresher.refresh(
            M3uRefreshRequest(
                sourceId = "source",
                sourceKind = SourceKinds.REMOTE_M3U,
                storedLocator = "https://provider.test/list.m3u",
            ),
        )

        assertEquals(
            SourceOnboardingResult.Failure(SourceOnboardingFailure.CatalogImportFailure),
            result,
        )
    }

    private fun playlist(): M3uPlaylist = M3uPlaylist(
        entries = listOf(
            M3uEntry(
                displayName = "Channel",
                streamUrl = "https://stream.test/live.m3u8",
            ),
        ),
    )
}
