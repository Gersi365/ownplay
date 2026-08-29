package app.ownplay.player.playback

internal enum class LiveActivityBackgroundAction {
    NONE,
    SUSPEND_AND_RETAIN_SURFACE,
}

/**
 * Activity-level policy for Live only. PiP keeps ownership of playback, while configuration
 * recreation is allowed to tear down through Activity destruction instead of background suspend.
 */
internal object LiveActivityLifecyclePolicy {
    fun backgroundAction(
        state: PlaybackState,
        inPictureInPicture: Boolean,
        changingConfigurations: Boolean,
    ): LiveActivityBackgroundAction {
        if (inPictureInPicture || changingConfigurations) {
            return LiveActivityBackgroundAction.NONE
        }

        val live = when (state) {
            is PlaybackState.Loading -> state.request.mediaKind == PlaybackMediaKind.LIVE
            is PlaybackState.Playing -> state.request.mediaKind == PlaybackMediaKind.LIVE
            is PlaybackState.Paused -> state.request.mediaKind == PlaybackMediaKind.LIVE
            PlaybackState.Idle,
            is PlaybackState.Failed,
            -> false
        }
        return if (live) {
            LiveActivityBackgroundAction.SUSPEND_AND_RETAIN_SURFACE
        } else {
            LiveActivityBackgroundAction.NONE
        }
    }
}
