package app.ownplay.player.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.source.SourceSyncState
import app.ownplay.player.ui.AboutSettingsContent
import app.ownplay.player.ui.BackupRestoreSettingsContent
import app.ownplay.player.ui.PlaylistManagementSubscreen
import app.ownplay.player.ui.live.TvLiveManagementScreen

internal enum class TvSettingsDestination(
    val title: String,
    val description: String,
) {
    PLAYLISTS(
        title = "Playlists",
        description = "Add, edit, refresh, enable or disable the media sources connected to OwnPlay.",
    ),
    LIVE_MANAGEMENT(
        title = "Live Management",
        description = "Organize Live categories and channels, including visibility, ordering and custom groups.",
    ),
    BACKUP_RESTORE(
        title = "Backup & Restore",
        description = "Create or restore a personalization backup without restoring provider credentials or secrets.",
    ),
    ABOUT(
        title = "About",
        description = "OwnPlay TV product information, version details and the product disclaimer.",
    ),
}

internal val tvSettingsDestinations = listOf(
    TvSettingsDestination.PLAYLISTS,
    TvSettingsDestination.LIVE_MANAGEMENT,
    TvSettingsDestination.BACKUP_RESTORE,
    TvSettingsDestination.ABOUT,
)

internal val defaultTvSettingsDestination = TvSettingsDestination.PLAYLISTS

@Composable
internal fun TvSettingsScreen(
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    onOpenSourceInLive: (String) -> Unit,
) {
    var openDestination by remember { mutableStateOf<TvSettingsDestination?>(null) }
    var focusedDestination by remember { mutableStateOf(defaultTvSettingsDestination) }
    var originatingDestination by remember { mutableStateOf(defaultTvSettingsDestination) }
    val rootFocusRequesters = remember {
        tvSettingsDestinations.associateWith { FocusRequester() }
    }

    BackHandler(enabled = openDestination != null) {
        openDestination = null
    }

    LaunchedEffect(openDestination) {
        if (openDestination == null) {
            focusedDestination = originatingDestination
            rootFocusRequesters.getValue(originatingDestination).requestFocus()
        }
    }

    val returnToRoot = {
        openDestination = null
    }

    when (openDestination) {
        TvSettingsDestination.PLAYLISTS -> {
            PlaylistManagementSubscreen(
                runtime = runtime,
                summaries = summaries,
                syncState = syncState,
                onBack = returnToRoot,
                onOpenInLive = onOpenSourceInLive,
                focusBackOnEntry = true,
            )
        }
        TvSettingsDestination.LIVE_MANAGEMENT -> {
            TvLiveManagementScreen(
                runtime = runtime,
                summaries = summaries.filter { summary -> summary.enabled },
                onBack = returnToRoot,
                focusFirstActionOnEntry = true,
            )
        }
        TvSettingsDestination.BACKUP_RESTORE -> {
            TvSettingsSubpage(
                title = "Backup & Restore",
                description = "Supported personalization can be exported or restored. Provider credentials and secrets remain excluded.",
                onBack = returnToRoot,
            ) {
                BackupRestoreSettingsContent()
            }
        }
        TvSettingsDestination.ABOUT -> {
            TvSettingsSubpage(
                title = "About",
                description = "OwnPlay TV information and build details.",
                onBack = returnToRoot,
            ) {
                AboutSettingsContent()
            }
        }
        null -> {
            TvSettingsRoot(
                summaries = summaries,
                focusedDestination = focusedDestination,
                focusRequesters = rootFocusRequesters,
                onFocused = { destination -> focusedDestination = destination },
                onOpen = { destination ->
                    originatingDestination = destination
                    openDestination = destination
                },
            )
        }
    }
}

@Composable
private fun TvSettingsRoot(
    summaries: List<PlaylistSourceSummary>,
    focusedDestination: TvSettingsDestination,
    focusRequesters: Map<TvSettingsDestination, FocusRequester>,
    onFocused: (TvSettingsDestination) -> Unit,
    onOpen: (TvSettingsDestination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Use Up/Down to choose a destination and OK to open it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            tvSettingsDestinations.forEach { destination ->
                TvSettingsDestinationRow(
                    destination = destination,
                    focused = focusedDestination == destination,
                    focusRequester = focusRequesters.getValue(destination),
                    onFocused = { onFocused(destination) },
                    onClick = { onOpen(destination) },
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 30.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = focusedDestination.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = focusedDestination.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = tvSettingsStatus(focusedDestination, summaries),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TvSettingsDestinationRow(
    destination: TvSettingsDestination,
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val containerColor = if (focused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (focused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) onFocused()
            },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = tvSettingsIcon(destination),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = destination.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TvSettingsSubpage(
    title: String,
    description: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        backFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.focusRequester(backFocusRequester),
            ) {
                Text("‹ Settings")
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

private fun tvSettingsIcon(destination: TvSettingsDestination): ImageVector =
    when (destination) {
        TvSettingsDestination.PLAYLISTS -> Icons.Filled.Folder
        TvSettingsDestination.LIVE_MANAGEMENT -> Icons.Filled.Tune
        TvSettingsDestination.BACKUP_RESTORE -> Icons.Filled.Download
        TvSettingsDestination.ABOUT -> Icons.Filled.Info
    }

private fun tvSettingsStatus(
    destination: TvSettingsDestination,
    summaries: List<PlaylistSourceSummary>,
): String =
    when (destination) {
        TvSettingsDestination.PLAYLISTS -> when (summaries.size) {
            0 -> "No playlists configured."
            1 -> "1 playlist configured."
            else -> "${summaries.size} playlists configured."
        }
        TvSettingsDestination.LIVE_MANAGEMENT -> {
            val enabledCount = summaries.count { summary -> summary.enabled }
            when (enabledCount) {
                0 -> "Add or enable a playlist before managing Live content."
                1 -> "1 enabled playlist is available for Live management."
                else -> "$enabledCount enabled playlists are available for Live management."
            }
        }
        TvSettingsDestination.BACKUP_RESTORE ->
            "Personalization backup only; provider credentials and secrets remain excluded."
        TvSettingsDestination.ABOUT ->
            "OwnPlay TV is a player and organizer for user-provided media sources."
    }
