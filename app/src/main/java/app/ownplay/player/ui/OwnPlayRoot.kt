package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
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
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    LaunchedEffect(isTelevision, contentVisible) {
        if (isTelevision && contentVisible) {
            withFrameNanos { }
            if (!focusManager.moveFocus(FocusDirection.Next)) {
                withFrameNanos { }
                focusManager.moveFocus(FocusDirection.Next)
            }
        }
    }

    AnimatedVisibility(
        visible = contentVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 140)),
    ) {
        OwnPlayApp(
            runtime = runtime,
            rotationFullscreenEnabled = rotationFullscreenEnabled,
            onPlaybackFullscreenChanged = onPlaybackFullscreenChanged,
            onPlaybackSurfaceActiveChanged = onPlaybackSurfaceActiveChanged,
            onLivePreviewActiveChanged = onLivePreviewActiveChanged,
        )
    }
}
