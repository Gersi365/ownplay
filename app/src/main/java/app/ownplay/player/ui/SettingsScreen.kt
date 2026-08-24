package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.personalization.AppOrientationMode
import app.ownplay.player.personalization.AppOrientationStore
import app.ownplay.player.source.SourceSyncState
import kotlinx.coroutines.launch

private enum class SettingsDestination {
    INTERFACE,
    CONTENT,
    PLAYBACK,
    REFRESH,
    ABOUT,
    LIVE_MANAGEMENT,
    PLAYLISTS,
}

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
    var destination by remember { mutableStateOf(SettingsDestination.CONTENT) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val context = LocalContext.current
    val orientationStore = remember(context) {
        AppOrientationStore(context.applicationContext)
    }
    val orientationMode by orientationStore.observe().collectAsState(
        initial = AppOrientationMode.PORTRAIT,
    )
    val scope = rememberCoroutineScope()

    if (isLandscape) {
        LandscapeSettingsShell(
            destination = destination,
            onDestinationChange = { destination = it },
            runtime = runtime,
            summaries = summaries,
            syncState = syncState,
            orientationMode = orientationMode,
            onSetOrientation = { mode -> scope.launch { orientationStore.set(mode) } },
            activeSourceName = activeSourceName,
            hasActivePlayback = hasActivePlayback,
            onOpenLive = onOpenLive,
            onOpenSourceInLive = onOpenSourceInLive,
            onStopPlayback = onStopPlayback,
        )
        return
    }

    when (destination) {
        SettingsDestination.LIVE_MANAGEMENT -> {
            LiveManagementScreen(
                runtime = runtime,
                summaries = summaries,
                onBack = { destination = SettingsDestination.CONTENT },
            )
            return
        }
        SettingsDestination.PLAYLISTS -> {
            PlaylistManagementSubscreen(
                runtime = runtime,
                summaries = summaries,
                syncState = syncState,
                onBack = { destination = SettingsDestination.CONTENT },
                onOpenInLive = onOpenSourceInLive,
            )
            return
        }
        else -> Unit
    }

    PortraitSettingsMenu(
        orientationMode = orientationMode,
        onSetOrientation = { mode -> scope.launch { orientationStore.set(mode) } },
        summaries = summaries,
        activeSourceName = activeSourceName,
        hasActivePlayback = hasActivePlayback,
        onOpenLive = onOpenLive,
        onStopPlayback = onStopPlayback,
        onOpenLiveManagement = { destination = SettingsDestination.LIVE_MANAGEMENT },
        onOpenPlaylists = { destination = SettingsDestination.PLAYLISTS },
    )
}

@Composable
private fun LandscapeSettingsShell(
    destination: SettingsDestination,
    onDestinationChange: (SettingsDestination) -> Unit,
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    orientationMode: AppOrientationMode,
    onSetOrientation: (AppOrientationMode) -> Unit,
    activeSourceName: String?,
    hasActivePlayback: Boolean,
    onOpenLive: () -> Unit,
    onOpenSourceInLive: (String) -> Unit,
    onStopPlayback: () -> Unit,
) {
    val selectedRailDestination = when (destination) {
        SettingsDestination.LIVE_MANAGEMENT,
        SettingsDestination.PLAYLISTS,
        -> SettingsDestination.CONTENT
        else -> destination
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "OwnPlay preferences",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsRailItem(
                    label = "Interface",
                    detail = "Orientation & layout",
                    selected = selectedRailDestination == SettingsDestination.INTERFACE,
                    onClick = { onDestinationChange(SettingsDestination.INTERFACE) },
                )
                SettingsRailItem(
                    label = "Content",
                    detail = "Live & playlists",
                    selected = selectedRailDestination == SettingsDestination.CONTENT,
                    onClick = { onDestinationChange(SettingsDestination.CONTENT) },
                )
                SettingsRailItem(
                    label = "Playback",
                    detail = "Preview & fullscreen",
                    selected = selectedRailDestination == SettingsDestination.PLAYBACK,
                    onClick = { onDestinationChange(SettingsDestination.PLAYBACK) },
                )
                SettingsRailItem(
                    label = "Refresh",
                    detail = "Source updates",
                    selected = selectedRailDestination == SettingsDestination.REFRESH,
                    onClick = { onDestinationChange(SettingsDestination.REFRESH) },
                )
                SettingsRailItem(
                    label = "About",
                    detail = "OwnPlay information",
                    selected = selectedRailDestination == SettingsDestination.ABOUT,
                    onClick = { onDestinationChange(SettingsDestination.ABOUT) },
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            when (destination) {
                SettingsDestination.INTERFACE -> LandscapeSectionPage(
                    title = "Interface",
                    subtitle = "Choose how OwnPlay is presented.",
                ) {
                    InterfaceSettingsContent(
                        orientationMode = orientationMode,
                        onSetOrientation = onSetOrientation,
                    )
                }

                SettingsDestination.CONTENT -> LandscapeSectionPage(
                    title = "Content",
                    subtitle = "Manage Live organization and playlist sources.",
                ) {
                    ContentSettingsContent(
                        summaries = summaries,
                        onOpenLiveManagement = {
                            onDestinationChange(SettingsDestination.LIVE_MANAGEMENT)
                        },
                        onOpenPlaylists = {
                            onDestinationChange(SettingsDestination.PLAYLISTS)
                        },
                    )
                }

                SettingsDestination.PLAYBACK -> LandscapeSectionPage(
                    title = "Playback",
                    subtitle = "Preview and full player behavior.",
                ) {
                    PlaybackSettingsContent(
                        activeSourceName = activeSourceName,
                        hasActivePlayback = hasActivePlayback,
                        onOpenLive = onOpenLive,
                        onStopPlayback = onStopPlayback,
                    )
                }

                SettingsDestination.REFRESH -> LandscapeSectionPage(
                    title = "Refresh",
                    subtitle = "How OwnPlay updates source content.",
                ) {
                    RefreshSettingsContent()
                }

                SettingsDestination.ABOUT -> LandscapeSectionPage(
                    title = "About",
                    subtitle = "OwnPlay media player and organizer.",
                ) {
                    AboutSettingsContent()
                }

                SettingsDestination.LIVE_MANAGEMENT -> LiveManagementScreen(
                    runtime = runtime,
                    summaries = summaries,
                    onBack = { onDestinationChange(SettingsDestination.CONTENT) },
                )

                SettingsDestination.PLAYLISTS -> PlaylistManagementSubscreen(
                    runtime = runtime,
                    summaries = summaries,
                    syncState = syncState,
                    onBack = { onDestinationChange(SettingsDestination.CONTENT) },
                    onOpenInLive = onOpenSourceInLive,
                )
            }
        }
    }
}

@Composable
private fun SettingsRailItem(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LandscapeSectionPage(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
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

@Composable
private fun PortraitSettingsMenu(
    orientationMode: AppOrientationMode,
    onSetOrientation: (AppOrientationMode) -> Unit,
    summaries: List<PlaylistSourceSummary>,
    activeSourceName: String?,
    hasActivePlayback: Boolean,
    onOpenLive: () -> Unit,
    onStopPlayback: () -> Unit,
    onOpenLiveManagement: () -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Interface, content management and playback.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CompactSettingsSection(
                title = "Interface",
                subtitle = "How OwnPlay is presented",
            ) {
                InterfaceSettingsContent(
                    orientationMode = orientationMode,
                    onSetOrientation = onSetOrientation,
                )
            }

            CompactSettingsSection(
                title = "Content",
                subtitle = "Manage what appears in OwnPlay",
            ) {
                ContentSettingsContent(
                    summaries = summaries,
                    onOpenLiveManagement = onOpenLiveManagement,
                    onOpenPlaylists = onOpenPlaylists,
                )
            }

            CompactSettingsSection(
                title = "Playback",
                subtitle = "Live preview and full player behavior",
            ) {
                PlaybackSettingsContent(
                    activeSourceName = activeSourceName,
                    hasActivePlayback = hasActivePlayback,
                    onOpenLive = onOpenLive,
                    onStopPlayback = onStopPlayback,
                )
            }

            CompactSettingsSection(
                title = "Refresh",
                subtitle = "Automatic source update when OwnPlay opens",
            ) {
                RefreshSettingsContent()
            }

            CompactSettingsSection(
                title = "About",
                subtitle = "OwnPlay media player and organizer",
            ) {
                AboutSettingsContent()
            }
        }
    }
}

@Composable
private fun InterfaceSettingsContent(
    orientationMode: AppOrientationMode,
    onSetOrientation: (AppOrientationMode) -> Unit,
) {
    SettingValueRow(
        label = "App orientation",
        value = if (orientationMode == AppOrientationMode.LANDSCAPE) {
            "Landscape"
        } else {
            "Portrait"
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OrientationButton(
            label = "Portrait",
            selected = orientationMode == AppOrientationMode.PORTRAIT,
            onClick = { onSetOrientation(AppOrientationMode.PORTRAIT) },
            modifier = Modifier.weight(1f),
        )
        OrientationButton(
            label = "Landscape",
            selected = orientationMode == AppOrientationMode.LANDSCAPE,
            onClick = { onSetOrientation(AppOrientationMode.LANDSCAPE) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = "The app shell stays in the selected orientation. Fullscreen playback follows the device sensor.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ContentSettingsContent(
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
}

@Composable
private fun PlaybackSettingsContent(
    activeSourceName: String?,
    hasActivePlayback: Boolean,
    onOpenLive: () -> Unit,
    onStopPlayback: () -> Unit,
) {
    SettingValueRow(label = "Channel tap", value = "Preview")
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingValueRow(label = "Fullscreen", value = "Manual · Sensor")
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
private fun RefreshSettingsContent() {
    SettingValueRow(label = "Sequence", value = "Channels → EPG")
    Text(
        text = "Existing channels remain usable while refresh runs. VOD and Series will join this pipeline later.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AboutSettingsContent() {
    Text(
        text = "OwnPlay plays and organizes media sources you add. It does not provide channels, subscriptions or IPTV services.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlaylistManagementSubscreen(
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    onBack: () -> Unit,
    onOpenInLive: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("‹ Settings") }
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            PlaylistSettingsScreen(
                runtime = runtime,
                summaries = summaries,
                syncState = syncState,
                onOpenInLive = onOpenInLive,
            )
        }
    }
}

@Composable
private fun OrientationButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(38.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(38.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CompactSettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    detail: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onClick) {
            Text(actionLabel)
        }
    }
}
