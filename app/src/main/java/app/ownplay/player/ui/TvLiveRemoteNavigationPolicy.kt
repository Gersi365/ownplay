package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackNavigationDirection

internal enum class TvLiveRemoteArrow {
    UP,
    DOWN,
    OTHER,
}

internal fun tvLiveRemoteNavigation(
    arrow: TvLiveRemoteArrow,
    keyDown: Boolean,
): PlaybackNavigationDirection? {
    if (!keyDown) return null
    return when (arrow) {
        TvLiveRemoteArrow.UP -> PlaybackNavigationDirection.PREVIOUS
        TvLiveRemoteArrow.DOWN -> PlaybackNavigationDirection.NEXT
        TvLiveRemoteArrow.OTHER -> null
    }
}
