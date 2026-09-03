package app.ownplay.player.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal enum class MediaCardVisualState {
    DEFAULT,
    SELECTED,
    FOCUSED,
}

internal fun mediaCardVisualState(
    focused: Boolean,
    selected: Boolean = false,
): MediaCardVisualState = when {
    focused -> MediaCardVisualState.FOCUSED
    selected -> MediaCardVisualState.SELECTED
    else -> MediaCardVisualState.DEFAULT
}

internal fun Modifier.mediaCardVisualTint(
    selected: Boolean = false,
    onFocused: ((Boolean) -> Unit)? = null,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val tint = when (mediaCardVisualState(focused = focused, selected = selected)) {
        MediaCardVisualState.FOCUSED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        MediaCardVisualState.SELECTED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        MediaCardVisualState.DEFAULT -> Color.Transparent
    }

    this
        .onFocusChanged { focusState ->
            val nextFocused = focusState.hasFocus
            if (focused != nextFocused) {
                focused = nextFocused
                onFocused?.invoke(nextFocused)
            }
        }
        .drawWithContent {
            drawContent()
            if (tint.alpha > 0f) {
                val radius = 12.dp.toPx()
                drawRoundRect(
                    color = tint,
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
}

internal fun Modifier.mediaPaneFocusMemory(): Modifier =
    this
        .focusRestorer()
        .focusGroup()
