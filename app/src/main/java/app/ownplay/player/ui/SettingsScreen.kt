package app.ownplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.source.SourceSyncState

@Composable
internal fun SettingsScreen(
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    activeSourceName: String?,
    hasActivePlayback: Boolean,
    onOpenLive: () -> Unit,
    onOpenSourceInLive: (String) -> Unit,
    onStopPlayback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Playlists, playback and OwnPlay behavior.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(
            title = "Playlists",
            subtitle = "Add, edit, delete and refresh your media sources",
        ) {
            PlaylistSettingsScreen(
                runtime = runtime,
                summaries = summaries,
                syncState = syncState,
                onOpenInLive = onOpenSourceInLive,
            )
        }

        SettingsCard(
            title = "Playback",
            subtitle = "Preview-first Live TV",
        ) {
            SettingValueRow(label = "Channel tap", value = "Preview")
            SettingValueRow(label = "Fullscreen", value = "Manual")
            SettingValueRow(label = "Primary orientation", value = "Portrait")
            SettingValueRow(label = "Active playlist", value = activeSourceName ?: "None")
            if (hasActivePlayback) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onOpenLive,
                        modifier = Modifier.weight(1f),
                    ) { Text("Back to Live") }
                    OutlinedButton(
                        onClick = onStopPlayback,
                        modifier = Modifier.weight(1f),
                    ) { Text("Stop") }
                }
            }
        }

        SettingsCard(
            title = "Automatic refresh",
            subtitle = "Runs whenever OwnPlay opens",
        ) {
            SettingValueRow(label = "1", value = "Channels")
            SettingValueRow(label = "2", value = "EPG")
            SettingValueRow(label = "Future", value = "VOD / Series")
            Text(
                text = "Existing channels stay available while a refresh is running. " +
                    "EPG starts only after the channel refresh finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(
            title = "About OwnPlay",
            subtitle = "Media player and organizer",
        ) {
            Text(
                text = "OwnPlay plays and organizes media sources you add. " +
                    "It does not provide channels, subscriptions, or IPTV services.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
