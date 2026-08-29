package app.ownplay.player.playback

/**
 * Describes why Live playback entered the dedicated fullscreen surface.
 *
 * USER keeps fullscreen active until the user explicitly exits it.
 * ROTATION lets a Smartphone return to Preview when the device rotates back to portrait.
 */
enum class LiveFullscreenEntryReason {
    USER,
    ROTATION,
}

/**
 * Single transition policy shared by portrait, landscape, and TV Live playback.
 * Device-specific input only decides whether rotation is allowed to request fullscreen;
 * the playback transition itself remains the same safe restart path everywhere.
 */
object LivePlaybackPresentationPolicy {
    fun shouldEnterFullscreenFromRotation(
        rotationFullscreenEnabled: Boolean,
        isLandscape: Boolean,
        hasSelection: Boolean,
        alreadyFullscreen: Boolean,
    ): Boolean =
        rotationFullscreenEnabled &&
            isLandscape &&
            hasSelection &&
            !alreadyFullscreen

    fun shouldReturnToPreviewFromRotation(
        rotationFullscreenEnabled: Boolean,
        isPortrait: Boolean,
        entryReason: LiveFullscreenEntryReason?,
        isFullscreen: Boolean,
    ): Boolean =
        rotationFullscreenEnabled &&
            isPortrait &&
            isFullscreen &&
            entryReason == LiveFullscreenEntryReason.ROTATION
}

/**
 * Executes the reliability-first Live handoff between independent Preview and Fullscreen surfaces.
 *
 * The current PlayerView must be detached before Compose switches presentation state; otherwise
 * Media3 can see the departing view as the previous target and attempt a surface transfer through
 * PlayerView.switchTargetView. Live intentionally restarts instead, so the safe sequence is:
 * detach old surface -> stop stream -> switch presentation -> restart the same Live request.
 *
 * This helper is deliberately generic over callbacks so the ordering is unit-testable and remains
 * scoped to Live presentation changes. VOD/Series/PiP keep their existing playback behavior.
 */
object LivePlaybackSurfaceHandoff {
    fun restartAcrossPresentation(
        detachCurrentSurface: () -> Boolean,
        stopPlayback: () -> Unit,
        switchPresentation: () -> Unit,
        startPlayback: () -> Unit,
    ): Boolean {
        val detached = detachCurrentSurface()
        stopPlayback()
        switchPresentation()
        startPlayback()
        return detached
    }
}

enum class LivePlaybackPresentationSurface {
    PREVIEW,
    FULLSCREEN,
}

data class LivePlaybackTransitionTarget(
    val surface: LivePlaybackPresentationSurface,
    val sourceId: String,
    val channelId: String,
) {
    companion object {
        fun preview(selection: LivePlaybackSelection): LivePlaybackTransitionTarget =
            from(selection, LivePlaybackPresentationSurface.PREVIEW)

        fun fullscreen(selection: LivePlaybackSelection): LivePlaybackTransitionTarget =
            from(selection, LivePlaybackPresentationSurface.FULLSCREEN)

        private fun from(
            selection: LivePlaybackSelection,
            surface: LivePlaybackPresentationSurface,
        ): LivePlaybackTransitionTarget = LivePlaybackTransitionTarget(
            surface = surface,
            sourceId = selection.request.sourceId,
            channelId = selection.request.channelId,
        )
    }
}

enum class LivePlaybackTransitionDecision {
    APPLIED,
    DUPLICATE,
}

/**
 * Deduplicates overlapping Live presentation requests without introducing a second playback queue.
 *
 * PlaybackController already cancels stale resolver work through its generation counter. This gate
 * therefore owns only presentation intent: the last presentation observed by Compose and the most
 * recent handoff requested but not yet acknowledged by composition. Repeated Back/ESC, duplicate
 * rotation callbacks, or manual + rotation fullscreen requests for the same channel are ignored.
 * A request toward the opposite destination is still accepted immediately; PlaybackController then
 * invalidates the older start through its normal generation semantics.
 */
class LivePlaybackTransitionGate {
    private var observedTarget: LivePlaybackTransitionTarget? = null
    private var requestedTarget: LivePlaybackTransitionTarget? = null

    fun reconcileObserved(target: LivePlaybackTransitionTarget?) {
        observedTarget = target
        if (target == null || requestedTarget == target) {
            requestedTarget = null
        }
    }

    fun requestHandoff(
        target: LivePlaybackTransitionTarget,
        detachCurrentSurface: () -> Boolean,
        stopPlayback: () -> Unit,
        switchPresentation: () -> Unit,
        startPlayback: () -> Unit,
    ): LivePlaybackTransitionDecision {
        if (target == observedTarget || target == requestedTarget) {
            return LivePlaybackTransitionDecision.DUPLICATE
        }

        val previousRequestedTarget = requestedTarget
        requestedTarget = target
        return try {
            LivePlaybackSurfaceHandoff.restartAcrossPresentation(
                detachCurrentSurface = detachCurrentSurface,
                stopPlayback = stopPlayback,
                switchPresentation = switchPresentation,
                startPlayback = startPlayback,
            )
            LivePlaybackTransitionDecision.APPLIED
        } catch (failure: Throwable) {
            if (requestedTarget == target) {
                requestedTarget = previousRequestedTarget
            }
            throw failure
        }
    }
}
