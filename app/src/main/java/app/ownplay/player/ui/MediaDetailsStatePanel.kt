package app.ownplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class MediaDetailsFocusTarget {
    PLAYBACK,
    RETRY,
    BACK,
    NONE,
}

internal fun mediaDetailsFocusTarget(
    isTelevision: Boolean,
    playbackReturnRequested: Boolean,
    errorActionAvailable: Boolean,
    backRequested: Boolean,
): MediaDetailsFocusTarget = when {
    !isTelevision -> MediaDetailsFocusTarget.NONE
    playbackReturnRequested -> MediaDetailsFocusTarget.PLAYBACK
    errorActionAvailable -> MediaDetailsFocusTarget.RETRY
    backRequested -> MediaDetailsFocusTarget.BACK
    else -> MediaDetailsFocusTarget.NONE
}

@Composable
internal fun MediaDetailsStatePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionFocusRequester: FocusRequester? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    loading -> CircularProgressIndicator(strokeWidth = 2.dp)
                    error -> Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (actionLabel != null && onAction != null) {
                val focusModifier = actionFocusRequester?.let { requester ->
                    Modifier.focusRequester(requester)
                } ?: Modifier
                Button(
                    onClick = onAction,
                    modifier = focusModifier,
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
