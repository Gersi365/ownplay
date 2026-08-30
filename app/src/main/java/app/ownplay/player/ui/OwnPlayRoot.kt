package app.ownplay.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.ownplay.player.OwnPlayAppRuntime

@Composable
fun OwnPlayRoot(
    runtime: OwnPlayAppRuntime,
    rotationFullscreenEnabled: Boolean = false,
    onPlaybackFullscreenChanged: (Boolean) -> Unit = {},
    onPlaybackSurfaceActiveChanged: (Boolean) -> Unit = {},
    onLivePreviewActiveChanged: (Boolean) -> Unit = {},
) {
    var contentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
            scaleIn(
                animationSpec = tween(durationMillis = 220),
                initialScale = 0.985f,
            ),
    ) {
        PlatformOwnPlayRoot(
            runtime = runtime,
            rotationFullscreenEnabled = rotationFullscreenEnabled,
            onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
            onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
            onLivePreviewActiveChanged = onLivePreviewActiveChanged,
        )
    }
}
