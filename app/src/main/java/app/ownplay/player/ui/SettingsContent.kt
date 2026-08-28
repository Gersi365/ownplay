package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import app.ownplay.player.persistence.PlaylistSourceSummary

@Composable
internal fun ContentSettingsContent(
    summaries: List<PlaylistSourceSummary>,
    onOpenLiveManagement: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    SettingsActionRow(
        title = "Live management",
        detail = "Categories · channels · groups",
        actionLabel = "Manage",
        onClick = onOpenLiveManagement,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsActionRow(
        title = "Playlists",
        detail = "${summaries.size} configured · sources & refresh",
        actionLabel = "Manage",
        onClick = onOpenPlaylists,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingsActionRow(
        title = "Downloads",
        detail = "Movies · series episodes · offline storage",
        actionLabel = "Open",
        onClick = onOpenDownloads,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    BackupRestoreSettingsContent()
}

@Composable
internal fun PlaybackSettingsContent(
    activeSourceName: String?,
    hasActivePlayback: Boolean,
    onOpenLive: () -> Unit,
    onStopPlayback: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

    SettingValueRow(label = "Channel tap", value = "Preview")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingValueRow(
        label = "Fullscreen",
        value = if (isTelevision) "Manual · Landscape" else "Manual · Sensor",
    )
    activeSourceName?.let { name ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingValueRow(label = "Active playlist", value = name)
    }
    if (hasActivePlayback) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = onOpenLive,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
            ) { Text("Back to Live") }
            OutlinedButton(
                onClick = onStopPlayback,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
            ) { Text("Stop") }
        }
    }
}

@Composable
internal fun RefreshSettingsContent() {
    SettingValueRow(label = "Startup refresh", value = "Automatic · background")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingValueRow(label = "Sequence", value = "Channels → EPG → Movies → Series")
    Text(
        text = "Saved catalogs remain visible while refresh runs, so navigation and playback can continue. Downloads run independently through WorkManager.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun AboutSettingsContent() {
    Text(
        text = "OwnPlay plays and organizes media sources you add. It does not provide channels, subscriptions or IPTV services.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
