package app.ownplay.player.ui

import app.ownplay.player.playback.PlaybackMediaKind

internal enum class PictureInPictureSurfaceBindingMode {
    MEDIA3_TRANSFER,
    DETACH_BEFORE_BIND,
}

/**
 * Keeps the existing Media3 target-transfer path for VOD/Series, while Live explicitly detaches
 * the current PlayerView before PiP binds another surface. This avoids reintroducing
 * PlayerView.switchTargetView for Live without changing PiP behavior for other media kinds.
 */
internal object PictureInPictureSurfaceHandoffPolicy {
    fun modeFor(mediaKind: PlaybackMediaKind?): PictureInPictureSurfaceBindingMode =
        if (mediaKind == PlaybackMediaKind.LIVE) {
            PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND
        } else {
            PictureInPictureSurfaceBindingMode.MEDIA3_TRANSFER
        }

    fun handoff(
        mode: PictureInPictureSurfaceBindingMode,
        detachCurrentSurface: () -> Unit,
        bindDestinationSurface: () -> Unit,
    ) {
        if (mode == PictureInPictureSurfaceBindingMode.DETACH_BEFORE_BIND) {
            detachCurrentSurface()
        }
        bindDestinationSurface()
    }
}
