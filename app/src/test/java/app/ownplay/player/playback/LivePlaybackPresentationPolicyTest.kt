package app.ownplay.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackPresentationPolicyTest {
    @Test
    fun `rotation enters fullscreen only from an active preview`() {
        assertTrue(
            LivePlaybackPresentationPolicy.shouldEnterFullscreenFromRotation(
                rotationFullscreenEnabled = true,
                isLandscape = true,
                hasSelection = true,
                alreadyFullscreen = false,
            ),
        )
        assertFalse(
            LivePlaybackPresentationPolicy.shouldEnterFullscreenFromRotation(
                rotationFullscreenEnabled = false,
                isLandscape = true,
                hasSelection = true,
                alreadyFullscreen = false,
            ),
        )
        assertFalse(
            LivePlaybackPresentationPolicy.shouldEnterFullscreenFromRotation(
                rotationFullscreenEnabled = true,
                isLandscape = false,
                hasSelection = true,
                alreadyFullscreen = false,
            ),
        )
        assertFalse(
            LivePlaybackPresentationPolicy.shouldEnterFullscreenFromRotation(
                rotationFullscreenEnabled = true,
                isLandscape = true,
                hasSelection = false,
                alreadyFullscreen = false,
            ),
        )
    }

    @Test
    fun `portrait returns only rotation-entered fullscreen to preview`() {
        assertTrue(
            LivePlaybackPresentationPolicy.shouldReturnToPreviewFromRotation(
                rotationFullscreenEnabled = true,
                isPortrait = true,
                entryReason = LiveFullscreenEntryReason.ROTATION,
                isFullscreen = true,
            ),
        )
        assertFalse(
            LivePlaybackPresentationPolicy.shouldReturnToPreviewFromRotation(
                rotationFullscreenEnabled = true,
                isPortrait = true,
                entryReason = LiveFullscreenEntryReason.USER,
                isFullscreen = true,
            ),
        )
        assertFalse(
            LivePlaybackPresentationPolicy.shouldReturnToPreviewFromRotation(
                rotationFullscreenEnabled = true,
                isPortrait = false,
                entryReason = LiveFullscreenEntryReason.ROTATION,
                isFullscreen = true,
            ),
        )
    }

    @Test
    fun `live surface handoff detaches before stop switch and restart`() {
        val events = mutableListOf<String>()

        val detached = LivePlaybackSurfaceHandoff.restartAcrossPresentation(
            detachCurrentSurface = {
                events += "detach"
                true
            },
            stopPlayback = { events += "stop" },
            switchPresentation = { events += "switch" },
            startPlayback = { events += "start" },
        )

        assertTrue(detached)
        assertEquals(listOf("detach", "stop", "switch", "start"), events)
    }

    @Test
    fun `live handoff still restarts when no surface is currently bound`() {
        val events = mutableListOf<String>()

        val detached = LivePlaybackSurfaceHandoff.restartAcrossPresentation(
            detachCurrentSurface = {
                events += "detach"
                false
            },
            stopPlayback = { events += "stop" },
            switchPresentation = { events += "switch" },
            startPlayback = { events += "start" },
        )

        assertFalse(detached)
        assertEquals(listOf("detach", "stop", "switch", "start"), events)
    }

    @Test
    fun `live teardown detaches before stop and presentation clear`() {
        val events = mutableListOf<String>()

        val detached = LivePlaybackSurfaceTeardown.stopAfterDetaching(
            detachCurrentSurface = {
                events += "detach"
                true
            },
            stopPlayback = { events += "stop" },
            clearPresentation = { events += "clear" },
        )

        assertTrue(detached)
        assertEquals(listOf("detach", "stop", "clear"), events)
    }

    @Test
    fun `live teardown still stops and clears when no surface is bound`() {
        val events = mutableListOf<String>()

        val detached = LivePlaybackSurfaceTeardown.stopAfterDetaching(
            detachCurrentSurface = {
                events += "detach"
                false
            },
            stopPlayback = { events += "stop" },
            clearPresentation = { events += "clear" },
        )

        assertFalse(detached)
        assertEquals(listOf("detach", "stop", "clear"), events)
    }

    @Test
    fun `transition gate ignores repeated request before compose acknowledgement`() {
        val gate = LivePlaybackTransitionGate()
        val target = LivePlaybackTransitionTarget(
            surface = LivePlaybackPresentationSurface.FULLSCREEN,
            sourceId = "source",
            channelId = "channel",
        )
        val events = mutableListOf<String>()

        val first = gate.requestHandoff(
            target = target,
            detachCurrentSurface = { events += "detach"; true },
            stopPlayback = { events += "stop" },
            switchPresentation = { events += "switch" },
            startPlayback = { events += "start" },
        )
        val duplicate = gate.requestHandoff(
            target = target,
            detachCurrentSurface = { events += "duplicate-detach"; true },
            stopPlayback = { events += "duplicate-stop" },
            switchPresentation = { events += "duplicate-switch" },
            startPlayback = { events += "duplicate-start" },
        )

        assertEquals(LivePlaybackTransitionDecision.APPLIED, first)
        assertEquals(LivePlaybackTransitionDecision.DUPLICATE, duplicate)
        assertEquals(listOf("detach", "stop", "switch", "start"), events)
    }

    @Test
    fun `transition gate accepts opposite destination before prior request is acknowledged`() {
        val gate = LivePlaybackTransitionGate()
        val fullscreen = LivePlaybackTransitionTarget(
            surface = LivePlaybackPresentationSurface.FULLSCREEN,
            sourceId = "source",
            channelId = "channel",
        )
        val preview = fullscreen.copy(surface = LivePlaybackPresentationSurface.PREVIEW)
        val events = mutableListOf<String>()

        gate.requestHandoff(
            target = fullscreen,
            detachCurrentSurface = { events += "fullscreen-detach"; true },
            stopPlayback = { events += "fullscreen-stop" },
            switchPresentation = { events += "fullscreen-switch" },
            startPlayback = { events += "fullscreen-start" },
        )
        val reverse = gate.requestHandoff(
            target = preview,
            detachCurrentSurface = { events += "preview-detach"; false },
            stopPlayback = { events += "preview-stop" },
            switchPresentation = { events += "preview-switch" },
            startPlayback = { events += "preview-start" },
        )

        assertEquals(LivePlaybackTransitionDecision.APPLIED, reverse)
        assertEquals(
            listOf(
                "fullscreen-detach",
                "fullscreen-stop",
                "fullscreen-switch",
                "fullscreen-start",
                "preview-detach",
                "preview-stop",
                "preview-switch",
                "preview-start",
            ),
            events,
        )
    }

    @Test
    fun `compose acknowledgement makes same destination duplicate until state changes`() {
        val gate = LivePlaybackTransitionGate()
        val preview = LivePlaybackTransitionTarget(
            surface = LivePlaybackPresentationSurface.PREVIEW,
            sourceId = "source",
            channelId = "channel",
        )
        gate.reconcileObserved(preview)

        val result = gate.requestHandoff(
            target = preview,
            detachCurrentSurface = { error("must not detach") },
            stopPlayback = { error("must not stop") },
            switchPresentation = { error("must not switch") },
            startPlayback = { error("must not start") },
        )

        assertEquals(LivePlaybackTransitionDecision.DUPLICATE, result)
    }

    @Test
    fun `failed handoff can be retried`() {
        val gate = LivePlaybackTransitionGate()
        val target = LivePlaybackTransitionTarget(
            surface = LivePlaybackPresentationSurface.FULLSCREEN,
            sourceId = "source",
            channelId = "channel",
        )

        runCatching {
            gate.requestHandoff(
                target = target,
                detachCurrentSurface = { true },
                stopPlayback = {},
                switchPresentation = { error("boom") },
                startPlayback = {},
            )
        }

        val retry = gate.requestHandoff(
            target = target,
            detachCurrentSurface = { true },
            stopPlayback = {},
            switchPresentation = {},
            startPlayback = {},
        )

        assertEquals(LivePlaybackTransitionDecision.APPLIED, retry)
    }
}
