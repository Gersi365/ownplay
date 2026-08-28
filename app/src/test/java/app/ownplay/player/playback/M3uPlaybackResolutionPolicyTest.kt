package app.ownplay.player.playback

import app.ownplay.player.persistence.secure.SensitiveValueRef
import app.ownplay.player.persistence.secure.SensitiveValueStore
import app.ownplay.player.source.CredentialRef
import app.ownplay.player.source.credential.CredentialStore
import app.ownplay.player.source.credential.XtreamCredentials
import app.ownplay.player.source.m3u.M3uSourceLocator
import app.ownplay.player.source.m3u.M3uSourceLocatorCodec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uPlaybackResolutionPolicyTest {
    @Test
    fun remoteM3uOptInAllowsHttpDirectStream() = runBlocking {
        val result = resolve(
            kind = PlaybackResolutionSourceKind.REMOTE_M3U,
            storedSourceLocator = M3uSourceLocatorCodec.encode(
                M3uSourceLocator(
                    value = "http://playlist.example.test/channels.m3u",
                    allowCleartext = true,
                ),
            ),
        )

        assertEquals(
            PlaybackResolutionResult.Success(
                ResolvedPlaybackLocator(
                    value = "http://stream.example.test/live.m3u8",
                    origin = ResolvedPlaybackOrigin.DIRECT,
                ),
            ),
            result,
        )
    }

    @Test
    fun localM3uOptInAllowsHttpStreamListedInFile() = runBlocking {
        val result = resolve(
            kind = PlaybackResolutionSourceKind.LOCAL_M3U,
            storedSourceLocator = M3uSourceLocatorCodec.encode(
                M3uSourceLocator(
                    value = "content://fixture/playlist.m3u",
                    allowCleartext = true,
                ),
            ),
        )

        assertEquals(
            PlaybackResolutionResult.Success(
                ResolvedPlaybackLocator(
                    value = "http://stream.example.test/live.m3u8",
                    origin = ResolvedPlaybackOrigin.DIRECT,
                ),
            ),
            result,
        )
    }

    @Test
    fun m3uWithoutOptInRejectsHttpDirectStream() = runBlocking {
        val result = resolve(
            kind = PlaybackResolutionSourceKind.REMOTE_M3U,
            storedSourceLocator = M3uSourceLocatorCodec.encode(
                M3uSourceLocator(
                    value = "https://playlist.example.test/channels.m3u",
                    allowCleartext = false,
                ),
            ),
        )

        assertEquals(
            PlaybackResolutionResult.Failure(PlaybackResolutionFailureReason.CLEARTEXT_NOT_ALLOWED),
            result,
        )
    }

    @Test
    fun legacyM3uLocatorRemainsCleartextDenied() = runBlocking {
        val result = resolve(
            kind = PlaybackResolutionSourceKind.REMOTE_M3U,
            storedSourceLocator = "http://playlist.example.test/legacy.m3u",
        )

        assertEquals(
            PlaybackResolutionResult.Failure(PlaybackResolutionFailureReason.CLEARTEXT_NOT_ALLOWED),
            result,
        )
    }

    private suspend fun resolve(
        kind: PlaybackResolutionSourceKind,
        storedSourceLocator: String,
    ): PlaybackResolutionResult {
        val stores = FixtureStores(
            mapOf(
                "source-ref" to storedSourceLocator,
                "stream-ref" to "ownplay-locator-v1|direct|http://stream.example.test/live.m3u8",
            ),
        )
        val resolver = LivePlaybackResolver(
            lookup = FakeLookup(
                source = PlaybackSourceRecord(
                    sourceId = "source",
                    sourceKind = kind,
                    locatorRef = "source-ref",
                    credentialRef = null,
                    enabled = true,
                ),
                channel = PlaybackChannelRecord(
                    channelId = "channel",
                    sourceId = "source",
                    streamLocatorRef = "stream-ref",
                    removed = false,
                ),
            ),
            sensitiveValueStore = stores,
            credentialStore = stores,
        )
        return resolver.resolve(
            PlaybackRequest(
                sourceId = "source",
                channelId = "channel",
            ),
        )
    }

    private class FakeLookup(
        private val source: PlaybackSourceRecord,
        private val channel: PlaybackChannelRecord,
    ) : PlaybackResolutionLookup {
        override suspend fun sourceById(sourceId: String): PlaybackSourceRecord = source
        override suspend fun channelById(channelId: String): PlaybackChannelRecord = channel
        override suspend fun movieById(movieId: String): PlaybackMovieRecord? = null
    }

    private class FixtureStores(
        private val sensitiveValues: Map<String, String>,
    ) : SensitiveValueStore, CredentialStore {
        override fun put(value: String): SensitiveValueRef = error("not used")
        override fun get(ref: SensitiveValueRef): String? = sensitiveValues[ref.value]
        override fun delete(ref: SensitiveValueRef) = Unit

        override fun put(credentials: XtreamCredentials): CredentialRef = error("not used")
        override fun get(ref: CredentialRef): XtreamCredentials? = null
        override fun delete(ref: CredentialRef) = Unit
    }
}
