package app.ownplay.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
internal fun mediaCardContainerColor(
    state: MediaCardVisualState,
    defaultColor: Color = MaterialTheme.colorScheme.surface,
): Color = when (state) {
    MediaCardVisualState.FOCUSED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    MediaCardVisualState.SELECTED -> MaterialTheme.colorScheme.surfaceVariant
    MediaCardVisualState.DEFAULT -> defaultColor
}
