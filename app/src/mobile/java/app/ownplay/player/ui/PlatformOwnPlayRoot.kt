package app.ownplay.player.ui

import androidx.compose.runtime.Composable
import app.ownplay.player.OwnPlayAppRuntime

@Composable
internal fun PlatformOwnPlayRoot(
    runtime: OwnPlayAppRuntime,
    rotationFullscreenEnabled: Boolean,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    OwnPlayApp(
        runtime = runtime,
        rotationFullscreenEnabled = rotationFullscreenEnabled,
        onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
        onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
        onLivePreviewActiveChanged = onLivePreviewActiveChanged,
    )
}
