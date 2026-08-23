package app.ownplay.player.ui

import androidx.compose.runtime.Composable
import app.ownplay.player.OwnPlayAppRuntime

@Composable
fun OwnPlayRoot(
    runtime: OwnPlayAppRuntime,
    onPlaybackFullscreenChanged: (Boolean) -> Unit = {},
) {
    OwnPlayApp(
        runtime = runtime,
        onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
    )
}
