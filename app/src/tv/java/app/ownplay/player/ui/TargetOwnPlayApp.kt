package app.ownplay.player.ui

import androidx.compose.runtime.Composable
import app.ownplay.player.OwnPlayAppRuntime

/** TV-only presentation entry point. Rotation-driven fullscreen is intentionally disabled. */
@Composable
internal fun TargetOwnPlayApp(
    runtime: OwnPlayAppRuntime,
    rotationFullscreenEnabled: Boolean,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    TVOwnPlayApp(
        runtime = runtime,
        onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
        onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
        onLivePreviewActiveChanged = onLivePreviewActiveChanged,
    )
}
