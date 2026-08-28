package app.ownplay.player.ui.tv

import app.ownplay.player.playback.PlaybackMediaKind
import app.ownplay.player.playback.PlaybackState

internal enum class TvBackgroundPlaybackAction {
    NONE,
    SUSPEND,
    PAUSE_AND_RESUME,
}

internal object TvPlaybackLifecyclePolicy {
    fun backgroundAction(state: PlaybackState): TvBackgroundPlaybackAction = when (state) {
        PlaybackState.Idle,
        is PlaybackState.Failed,
        -> TvBackgroundPlaybackAction.NONE

        is PlaybackState.Loading -> TvBackgroundPlaybackAction.SUSPEND

        is PlaybackState.Playing -> if (state.request.mediaKind == PlaybackMediaKind.LIVE) {
            TvBackgroundPlaybackAction.SUSPEND
        } else {
            TvBackgroundPlaybackAction.PAUSE_AND_RESUME
        }

        is PlaybackState.Paused -> if (state.request.mediaKind == PlaybackMediaKind.LIVE) {
            TvBackgroundPlaybackAction.SUSPEND
        } else {
            TvBackgroundPlaybackAction.NONE
        }
    }
}
