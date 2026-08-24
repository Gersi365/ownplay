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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Playback, Live management and OwnPlay behavior.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(
            title = "Interface",
            subtitle = "Choose the app layout orientation",
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                text = "Rotating the phone does not rotate the app shell. Fullscreen playback still follows the device sensor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(
            title = "Live management",
            subtitle = "Keep Live simple; organize content here",
        ) {
            SettingValueRow(label = "Categories", value = "Order · Hide")
            SettingValueRow(label = "Channels", value = "Order · Hide · Rename")
            SettingValueRow(label = "Groups", value = "Create · Edit")
            Button(
                onClick = { showLiveManagement = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Live management")
            }
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
            SettingValueRow(label = "Fullscreen", value = "Manual · Sensor")
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
private fun OrientationButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
