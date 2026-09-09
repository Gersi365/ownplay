package app.ownplay.player.ui

import androidx.compose.runtime.Composable
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.ui.rebuild.RebuiltOwnPlayTvApp

/**
 * OwnPlay TV rebuild entry point.
 *
 * The legacy TV presentation tree is intentionally not referenced from this source set.
 */
@Composable
internal fun TargetOwnPlayApp(
    runtime: OwnPlayAppRuntime,
    rotationFullscreenEnabled: Boolean,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    RebuiltOwnPlayTvApp(
        runtime = runtime,
        onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
        onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
        onLivePreviewActiveChanged = onLivePreviewActiveChanged,
    )
}
