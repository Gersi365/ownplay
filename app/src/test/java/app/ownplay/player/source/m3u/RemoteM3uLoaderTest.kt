package app.ownplay.player.source.m3u

import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceResult
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteM3uLoaderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun load_parsesRemotePlaylistFixture() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                    #EXTM3U
                    #EXTINF:-1 tvg-id="fixture.1" group-title="Fixture",Fixture Channel
                    https://stream.example/live/fixture.m3u8
                    """.trimIndent(),
                )
                .build(),
        )
        val loader = RemoteM3uLoader(allowCleartext = true)

        val result = loader.load(server.url("/playlist.m3u").toString())

        assertTrue(result is SourceResult.Success)
        val playlist = (result as SourceResult.Success).value
        assertEquals(1, playlist.entries.size)
        assertEquals("fixture.1", playlist.entries.single().tvgId)
        assertEquals("Fixture Channel", playlist.entries.single().displayName)
    }

    @Test
    fun load_rejectsCleartextWithoutExplicitOptIn() = runBlocking {
        val loader = RemoteM3uLoader()

        val result = loader.load(server.url("/playlist.m3u").toString())

        assertEquals(
            SourceResult.Failure(SourceError.CleartextTransportRequiresOptIn),
            result,
        )
    }

    @Test
    fun load_emptySuccessfulBody_isMalformedPlaylist() = runBlocking {
        server.enqueue(MockResponse.Builder().body("\n# comment only\n").build())
        val loader = RemoteM3uLoader(allowCleartext = true)

        val result = loader.load(server.url("/empty.m3u").toString())

        assertEquals(SourceResult.Failure(SourceError.MalformedPlaylist), result)
    }

    @Test
    fun load_mapsHttpFailureWithoutEmbeddingUrl() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).body("not found").build())
        val loader = RemoteM3uLoader(allowCleartext = true)

        val result = loader.load(server.url("/missing.m3u?token=fixture-secret").toString())

        assertEquals(SourceResult.Failure(SourceError.HttpFailure(404)), result)
    }
}
