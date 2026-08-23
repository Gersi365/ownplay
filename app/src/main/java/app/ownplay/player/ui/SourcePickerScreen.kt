package app.ownplay.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.persistence.PlaylistSourceEntity
import app.ownplay.player.playback.LivePlaybackSelection
import app.ownplay.player.playback.PlaybackFailureCategory
import app.ownplay.player.playback.PlaybackState

@Composable
internal fun SourcePickerScreen(
    sources: List<PlaylistSourceEntity>,
    activeSelection: LivePlaybackSelection?,
    playbackState: PlaybackState,
    onSourceSelected: (String) -> Unit,
    onResumePlayback: (LivePlaybackSelection) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "OwnPlay",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your media. Your way.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (activeSelection != null && playbackState !is PlaybackState.Idle) {
                ActivePlaybackBar(
                    selection = activeSelection,
                    state = playbackState,
                    onOpen = { onResumePlayback(activeSelection) },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sources",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = sources,
                    key = PlaylistSourceEntity::sourceId,
                ) { source ->
                    SourceRow(
                        source = source,
                        onOpen = { onSourceSelected(source.sourceId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: PlaylistSourceEntity,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = source.enabled, onClick = onOpen),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (source.enabled) "Ready" else "Disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (source.enabled) {
                TextButton(onClick = onOpen) {
                    Text("Live")
                }
            }
        }
    }
}

@Composable
private fun ActivePlaybackBar(
    selection: LivePlaybackSelection,
    state: PlaybackState,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selection.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playbackStatusLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) {
                Text("Open")
            }
        }
    }
}

internal fun playbackStatusLabel(state: PlaybackState): String = when (state) {
    PlaybackState.Idle -> "Idle"
    is PlaybackState.Loading -> "Starting playback…"
    is PlaybackState.Playing -> "Playing"
    is PlaybackState.Paused -> "Paused"
    is PlaybackState.Failed -> when (state.failure.category) {
        PlaybackFailureCategory.NETWORK_UNAVAILABLE -> "Network unavailable"
        PlaybackFailureCategory.TIMEOUT -> "Playback timed out"
        PlaybackFailureCategory.AUTHENTICATION_FAILURE -> "Authentication failed"
        PlaybackFailureCategory.STREAM_UNAVAILABLE -> "Stream unavailable"
        PlaybackFailureCategory.UNSUPPORTED_MEDIA -> "Unsupported media"
        PlaybackFailureCategory.UNKNOWN -> "Playback failed"
    }
}
