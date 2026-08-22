package app.ownplay.player.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControllerTest {
    @Test
    fun successfulResolutionPreparesEngineWithoutLeakingLocatorIntoState() = runBlocking {
        val secretLocator = "https://stream.example.test/live.m3u8?token=controller-secret"
        val engine = FakeEngine()
        val controller = controller(
            engine = engine,
            resolver = {
                PlaybackResolutionResult.Success(
                    ResolvedPlaybackLocator(secretLocator, ResolvedPlaybackOrigin.DIRECT),
                )
            },
        )

        controller.start(request())

        assertEquals(secretLocator, engine.preparedLocator?.value)
        assertTrue(controller.state.value is PlaybackState.Loading)
        assertFalse(controller.state.value.toString().contains("controller-secret"))

        engine.emitReady()

        assertTrue(controller.state.value is PlaybackState.Playing)
        controller.close()
    }

    @Test
    fun resolutionFailureMapsConservativelyWithoutCallingEnginePrepare() = runBlocking {
        val engine = FakeEngine()
        val controller = controller(
            engine = engine,
            resolver = {
                PlaybackResolutionResult.Failure(
                    PlaybackResolutionFailureReason.CREDENTIALS_NOT_FOUND,
                )
            },
        )

        controller.start(request())

        val failed = controller.state.value as PlaybackState.Failed
        assertEquals(PlaybackFailureCategory.UNKNOWN, failed.failure.category)
        assertEquals(null, engine.preparedLocator)
        controller.close()
    }

    @Test
    fun playerFailureMapsIntoExistingFailureContractAndRetryRestartsResolution() = runBlocking {
        val engine = FakeEngine()
        var resolveCount = 0
        val controller = controller(
            engine = engine,
            resolver = {
                resolveCount += 1
                PlaybackResolutionResult.Success(
                    ResolvedPlaybackLocator(
                        "https://stream.example.test/live",
                        ResolvedPlaybackOrigin.DIRECT,
                    ),
                )
            },
        )

        controller.start(request())
        engine.emitReady()
        engine.emitFailure(PlaybackFailure(PlaybackFailureCategory.NETWORK_UNAVAILABLE))

        val failed = controller.state.value as PlaybackState.Failed
        assertTrue(failed.failure.retryable)

        controller.retry()

        assertEquals(2, resolveCount)
        assertTrue(controller.state.value is PlaybackState.Loading)
        controller.close()
    }

    @Test
    fun pauseBeforeReadyRemainsPausedWhenEngineBecomesReady() = runBlocking {
        val engine = FakeEngine()
        val controller = controller(
            engine = engine,
            resolver = {
                PlaybackResolutionResult.Success(
                    ResolvedPlaybackLocator(
                        "https://stream.example.test/live",
                        ResolvedPlaybackOrigin.DIRECT,
                    ),
                )
            },
        )

        controller.start(request())
        controller.pause()
        engine.emitReady()

        assertTrue(controller.state.value is PlaybackState.Paused)
        controller.close()
    }

    @Test
    fun loadingTimeoutFailsInsteadOfLeavingEndlessLoading() = runBlocking {
        val engine = FakeEngine()
        val controller = controller(
            engine = engine,
            loadingTimeoutMillis = 10L,
            resolver = {
                delay(100L)
                PlaybackResolutionResult.Success(
                    ResolvedPlaybackLocator(
                        "https://stream.example.test/live",
                        ResolvedPlaybackOrigin.DIRECT,
                    ),
                )
            },
        )

        controller.start(request())
        delay(30L)

        val failed = controller.state.value as PlaybackState.Failed
        assertEquals(PlaybackFailureCategory.TIMEOUT, failed.failure.category)
        assertTrue(engine.stopCount >= 2)
        controller.close()
    }

    @Test
    fun closeReleasesEngineAndResetsState() {
        val engine = FakeEngine()
        val controller = controller(
            engine = engine,
            resolver = {
                PlaybackResolutionResult.Failure(
                    PlaybackResolutionFailureReason.SOURCE_NOT_FOUND,
                )
            },
        )

        controller.close()

        assertTrue(engine.released)
        assertTrue(controller.state.value is PlaybackState.Idle)
    }

    @Test
    fun resolutionFailureMappingNeverInventsAuthenticationWithoutProviderEvidence() {
        listOf(
            PlaybackResolutionFailureReason.CREDENTIAL_REFERENCE_MISSING,
            PlaybackResolutionFailureReason.CREDENTIAL_REFERENCE_INVALID,
            PlaybackResolutionFailureReason.CREDENTIALS_NOT_FOUND,
            PlaybackResolutionFailureReason.CREDENTIALS_INVALID,
            PlaybackResolutionFailureReason.CREDENTIAL_STORE_FAILURE,
        ).forEach { reason ->
            assertEquals(
                PlaybackFailureCategory.UNKNOWN,
                reason.toPlaybackFailure().category,
            )
        }
    }

    private fun controller(
        engine: FakeEngine,
        loadingTimeoutMillis: Long = 10_000L,
        resolver: suspend (PlaybackRequest) -> PlaybackResolutionResult,
    ) = PlaybackController(
        resolveLocator = resolver,
        engine = engine,
        mainDispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
        loadingTimeoutMillis = loadingTimeoutMillis,
    )

    private fun request() = PlaybackRequest(
        sourceId = "source",
        channelId = "channel",
    )

    private class FakeEngine : PlaybackEngine {
        private var listener: PlaybackEngine.Listener? = null
        var preparedLocator: ResolvedPlaybackLocator? = null
            private set
        var stopCount: Int = 0
            private set
        var released: Boolean = false
            private set

        override fun setListener(listener: PlaybackEngine.Listener?) {
            this.listener = listener
        }

        override fun prepare(locator: ResolvedPlaybackLocator) {
            preparedLocator = locator
        }

        override fun play() = Unit
        override fun pause() = Unit

        override fun stop() {
            stopCount += 1
            preparedLocator = null
        }

        override fun release() {
            released = true
        }

        fun emitReady() = listener?.onReady()
        fun emitFailure(failure: PlaybackFailure) = listener?.onFailure(failure)
    }
}
