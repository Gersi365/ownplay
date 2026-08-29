package app.ownplay.player.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.ownplay.player.persistence.PlaylistSourceSummary

@Composable
internal fun ContentSettingsContent(
    summaries: List<PlaylistSourceSummary>,
    onOpenLiveManagement: () -> Unit,
    onOpenPlaylists: () -> Unit,
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
    BackupRestoreSettingsContent()
}

@Composable
internal fun AboutSettingsContent() {
    Text(
        text = "OwnPlay plays and organizes media sources you add. It does not provide channels, subscriptions or IPTV services.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
