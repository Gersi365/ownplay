package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackFailureCategory
import app.ownplay.player.playback.PlaybackState

internal fun playbackFailureLabel(category: PlaybackFailureCategory): String = when (category) {
    PlaybackFailureCategory.NETWORK_UNAVAILABLE -> "Network unavailable"
    PlaybackFailureCategory.TIMEOUT -> "Playback timed out"
    PlaybackFailureCategory.AUTHENTICATION_FAILURE -> "Authentication failed"
    PlaybackFailureCategory.STREAM_UNAVAILABLE -> "Stream unavailable"
    PlaybackFailureCategory.INSECURE_TRANSPORT_BLOCKED -> "Insecure HTTP is blocked for this source"
    PlaybackFailureCategory.UNSUPPORTED_MEDIA -> "Unsupported media"
    PlaybackFailureCategory.UNKNOWN -> "Playback failed"
}

internal fun playbackStatusLabel(state: PlaybackState): String = when (state) {
    PlaybackState.Idle -> "Ready"
    is PlaybackState.Loading -> "Starting playback…"
    is PlaybackState.Playing -> if (state.buffering) "Buffering…" else "Playing"
    is PlaybackState.Paused -> "Paused"
    is PlaybackState.Failed -> playbackFailureLabel(state.failure.category)
}
