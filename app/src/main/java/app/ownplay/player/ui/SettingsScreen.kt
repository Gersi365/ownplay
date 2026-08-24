package app.ownplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
    var showLiveManagement by remember { mutableStateOf(false) }
    if (showLiveManagement) {
        LiveManagementScreen(
            runtime = runtime,
            summaries = summaries,
            onBack = { showLiveManagement = false },
        )
        return
    }

    val context = LocalContext.current
    val orientationStore = remember(context) {
        AppOrientationStore(context.applicationContext)
    }
    val orientationMode by orientationStore.observe().collectAsState(
        initial = AppOrientationMode.PORTRAIT,
    )
    val scope = rememberCoroutineScope()

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
                        onClick = {
                            scope.launch { orientationStore.set(AppOrientationMode.PORTRAIT) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    OrientationButton(
                        label = "Landscape",
                        selected = orientationMode == AppOrientationMode.LANDSCAPE,
                        onClick = {
                            scope.launch { orientationStore.set(AppOrientationMode.LANDSCAPE) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "The app shell stays in the selected orientation. Fullscreen playback follows the device sensor.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CompactSettingsSection(
                title = "Live management",
                subtitle = "Keep the Live screen focused on watching",
            ) {
                SettingsActionRow(
                    title = "Categories, channels & groups",
                    detail = "Reorder · hide · rename · customize",
                    actionLabel = "Manage",
                    onClick = { showLiveManagement = true },
                )
            }

            CompactSettingsSection(
                title = "Playback",
                subtitle = "Live preview and full player behavior",
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

            PlaylistSettingsScreen(
                runtime = runtime,
                summaries = summaries,
                syncState = syncState,
                onOpenInLive = onOpenSourceInLive,
            )

            CompactSettingsSection(
                title = "Refresh",
                subtitle = "Automatic source update when OwnPlay opens",
            ) {
                SettingValueRow(label = "Sequence", value = "Channels → EPG")
                Text(
                    text = "Existing channels remain usable while refresh runs. VOD and Series will join this pipeline later.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CompactSettingsSection(
                title = "About",
                subtitle = "OwnPlay media player and organizer",
            ) {
                Text(
                    text = "OwnPlay plays and organizes media sources you add. It does not provide channels, subscriptions or IPTV services.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
