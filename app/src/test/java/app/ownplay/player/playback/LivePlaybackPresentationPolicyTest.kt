package app.ownplay.player.playback

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
}
