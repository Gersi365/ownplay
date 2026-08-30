package app.ownplay.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import app.ownplay.player.OwnPlayAppRuntime

@Composable
internal fun PlatformOwnPlayRoot(
    runtime: OwnPlayAppRuntime,
    rotationFullscreenEnabled: Boolean,
    onPlaybackFullscreenChanged: (Boolean) -> Unit,
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit,
    onLivePreviewActiveChanged: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!focusManager.moveFocus(FocusDirection.Next)) {
            withFrameNanos { }
            focusManager.moveFocus(FocusDirection.Next)
        }
    }

    OwnPlayTvApp(
        runtime = runtime,
        onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
        onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
        onLivePreviewActiveChanged = onLivePreviewActiveChanged,
    )
}
