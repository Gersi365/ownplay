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
}
