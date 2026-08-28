package app.ownplay.player.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackgroundSuspensionTest {
    @Test
    fun playingSessionReleasesEngineAndRestoresPlayingAfterBackground() = runBlocking {
        val engine = FakeEngine()
        val controller = controller(engine)
        val request = request()

        controller.start(request)
        engine.emitReady()
        assertTrue(controller.state.value is PlaybackState.Playing)

        val stopsBeforeBackground = engine.stopCount
        controller.suspendForBackground()

        val suspended = controller.state.value as PlaybackState.Paused
        assertEquals(request, suspended.request)
        assertTrue(engine.stopCount > stopsBeforeBackground)
        assertNull(engine.preparedLocator)

        engine.emitFailure(PlaybackFailure(PlaybackFailureCategory.UNKNOWN))
        assertTrue(controller.state.value is PlaybackState.Paused)

        controller.resumeAfterBackground()
        assertTrue(controller.state.value is PlaybackState.Loading)
        assertTrue(engine.preparedLocator != null)

        engine.emitReady()
        assertTrue(controller.state.value is PlaybackState.Playing)
        controller.close()
    }

    @Test
    fun pausedSessionReleasesEngineAndRestoresPausedAfterBackground() = runBlocking {
        val engine = FakeEngine()
        val controller = controller(engine)
        val request = request()

        controller.start(request)
        engine.emitReady()
        controller.pause()
        assertTrue(controller.state.value is PlaybackState.Paused)

        controller.suspendForBackground()
        assertTrue(controller.state.value is PlaybackState.Paused)
        assertNull(engine.preparedLocator)

        controller.resumeAfterBackground()
        assertTrue(controller.state.value is PlaybackState.Loading)
        assertTrue(engine.pauseCount > 0)

        engine.emitReady()
        val restored = controller.state.value as PlaybackState.Paused
        assertEquals(request, restored.request)
        controller.close()
    }

    private fun controller(engine: FakeEngine) = PlaybackController(
        resolveLocator = {
            PlaybackResolutionResult.Success(
                ResolvedPlaybackLocator(
                    value = "https://stream.example.test/live",
                    origin = ResolvedPlaybackOrigin.DIRECT,
                ),
            )
        },
        engine = engine,
        mainDispatcher = Dispatchers.Unconfined,
        ioDispatcher = Dispatchers.Unconfined,
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
        var pauseCount: Int = 0
            private set

        override fun setListener(listener: PlaybackEngine.Listener?) {
            this.listener = listener
        }

        override fun prepare(locator: ResolvedPlaybackLocator) {
            preparedLocator = locator
        }

        override fun play() = Unit

        override fun pause() {
            pauseCount += 1
        }

        override fun stop() {
            stopCount += 1
            preparedLocator = null
        }

        override fun release() = Unit

        fun emitReady() = listener?.onReady()

        fun emitFailure(failure: PlaybackFailure) = listener?.onFailure(failure)
    }
}
