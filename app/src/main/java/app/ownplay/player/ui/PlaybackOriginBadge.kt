package app.ownplay.player.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.playback.ResolvedPlaybackOrigin

@Composable
internal fun PlaybackOriginBadge(
    origin: ResolvedPlaybackOrigin,
    modifier: Modifier = Modifier,
) {
    val offline = origin == ResolvedPlaybackOrigin.LOCAL_DOWNLOAD
    val label = if (offline) "OFFLINE FILE" else "STREAMING"
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (offline) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        },
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (offline) Icons.Filled.DownloadDone else Icons.Filled.Cloud,
                contentDescription = if (offline) "Playing offline file" else "Streaming online",
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
