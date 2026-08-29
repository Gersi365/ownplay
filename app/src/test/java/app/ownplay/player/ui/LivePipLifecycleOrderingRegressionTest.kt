package app.ownplay.player.ui

import app.ownplay.player.playback.LiveActivityBackgroundAction
import app.ownplay.player.playback.LiveActivityLifecyclePolicy
import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackRequest
import app.ownplay.player.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePipLifecycleOrderingRegressionTest {
    @Test
    fun `background retains and resumes preview while pip restore avoids background suspension`() {
        val background = LiveLifecycleSurfaceHarness()

        background.openPreview()
        background.activityStop(inPictureInPicture = false)

        background.assertNone()
        assertEquals(SurfaceId.PREVIEW, background.lifecycleRetainedSurface)
        assertEquals(1, background.backgroundSuspensionCount)

        background.activityResume()

        background.assertOnly(SurfaceId.PREVIEW)
        assertNull(background.lifecycleRetainedSurface)
        assertEquals(1, background.lifecycleSurfaceResumeCount)

        val pip = LiveLifecycleSurfaceHarness()

        pip.openPreview()
        pip.enterPictureInPicture()
        pip.assertOnly(SurfaceId.PIP)
        assertEquals(SurfaceId.PREVIEW, pip.pictureInPictureReturnTarget)

        pip.activityStop(inPictureInPicture = true)

        pip.assertOnly(SurfaceId.PIP)
        assertNull(pip.lifecycleRetainedSurface)
        assertEquals(0, pip.backgroundSuspensionCount)

        pip.returnFromPictureInPicture()
        pip.assertOnly(SurfaceId.PREVIEW)
        assertNull(pip.pictureInPictureReturnTarget)

        pip.activityResume()

        pip.assertOnly(SurfaceId.PREVIEW)
        assertEquals(0, pip.lifecycleSurfaceResumeCount)
        assertEquals(0, pip.backgroundSuspensionCount)
        assertTrue(
            "PiP exit and resume must never create a competing Live surface",
            pip.maximumBoundSurfaceCount <= 1,
        )
    }
}

private enum class SurfaceId {
    PREVIEW,
    PIP,
}

private class LiveLifecycleSurfaceHarness {
    private val boundSurfaces = linkedSetOf<SurfaceId>()
    private val attachedSurfaces = mutableSetOf<SurfaceId>()

    var pictureInPictureReturnTarget: SurfaceId? = null
        private set

    var lifecycleRetainedSurface: SurfaceId? = null
        private set

    var backgroundSuspensionCount: Int = 0
        private set

    var lifecycleSurfaceResumeCount: Int = 0
        private set

    var maximumBoundSurfaceCount: Int = 0
        private set

    fun openPreview() {
        attachAndBind(SurfaceId.PREVIEW)
    }

    fun enterPictureInPicture() {
        pictureInPictureReturnTarget = boundSurfaces.singleOrNull()
            ?.takeIf { it in attachedSurfaces }

        PictureInPictureSurfaceHandoffPolicy.handoff(
            mode = PictureInPictureSurfaceHandoffPolicy.modeFor(PlaybackMediaKind.LIVE),
            detachCurrentSurface = { detachCurrent() },
            bindDestinationSurface = { attachAndBind(SurfaceId.PIP) },
        )
    }

    fun activityStop(inPictureInPicture: Boolean) {
        when (
            LiveActivityLifecyclePolicy.backgroundAction(
                state = PlaybackState.Playing(liveRequest()),
                inPictureInPicture = inPictureInPicture,
                changingConfigurations = false,
            )
        ) {
            LiveActivityBackgroundAction.SUSPEND_AND_RETAIN_SURFACE -> {
                lifecycleRetainedSurface = boundSurfaces.singleOrNull()
                backgroundSuspensionCount += 1
                detachCurrent()
            }
            LiveActivityBackgroundAction.NONE -> Unit
        }
    }

    fun activityResume() {
        val retained = lifecycleRetainedSurface
        lifecycleRetainedSurface = null
        if (retained != null && retained in attachedSurfaces) {
            bind(retained)
            lifecycleSurfaceResumeCount += 1
        }
    }

    fun returnFromPictureInPicture() {
        val target = pictureInPictureReturnTarget
            ?.takeIf { it in attachedSurfaces }
        val mode = PictureInPictureSurfaceHandoffPolicy.modeFor(PlaybackMediaKind.LIVE)

        if (target != null) {
            PictureInPictureSurfaceHandoffPolicy.handoff(
                mode = mode,
                detachCurrentSurface = { detachCurrent() },
                bindDestinationSurface = { bind(target) },
            )
        } else if (mode == PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND) {
            detachCurrent()
        }

        attachedSurfaces.remove(SurfaceId.PIP)
        pictureInPictureReturnTarget = null
    }

    fun assertOnly(surface: SurfaceId) {
        assertEquals(setOf(surface), boundSurfaces)
    }

    fun assertNone() {
        assertTrue(boundSurfaces.isEmpty())
    }

    private fun attachAndBind(surface: SurfaceId) {
        attachedSurfaces += surface
        bind(surface)
    }

    private fun bind(surface: SurfaceId) {
        boundSurfaces += surface
        maximumBoundSurfaceCount = maxOf(maximumBoundSurfaceCount, boundSurfaces.size)
    }

    private fun detachCurrent(): Boolean {
        val current = boundSurfaces.singleOrNull() ?: return false
        boundSurfaces.remove(current)
        return true
    }

    private fun liveRequest() = PlaybackRequest(
        sourceId = "source-a",
        channelId = "channel-1",
        mediaKind = PlaybackMediaKind.LIVE,
    )
}
