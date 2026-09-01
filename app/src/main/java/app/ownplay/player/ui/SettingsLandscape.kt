package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.personalization.AppOrientationMode
import app.ownplay.player.source.SourceSyncState

@Composable
internal fun LandscapeSettingsShell(
    destination: SettingsDestination,
    onDestinationChange: (SettingsDestination) -> Unit,
    runtime: OwnPlayAppRuntime,
    summaries: List<PlaylistSourceSummary>,
    syncState: SourceSyncState,
    deviceProfile: AppDeviceProfile?,
    orientationMode: AppOrientationMode,
    onSetOrientation: (AppOrientationMode) -> Unit,
    onOpenSourceInLive: (String) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val resolvedDestination = if (isTelevision && destination == SettingsDestination.DOWNLOADS) {
        SettingsDestination.CONTENT
    } else {
        destination
    }
    val selectedRailFocusRequester = remember { FocusRequester() }
    val selectedRailDestination = when (resolvedDestination) {
        SettingsDestination.LIVE_MANAGEMENT,
        SettingsDestination.PLAYLISTS,
        -> SettingsDestination.CONTENT
        else -> resolvedDestination
    }

    LaunchedEffect(isTelevision, resolvedDestination) {
        if (isTelevision) {
            selectedRailFocusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LandscapeSettingsRail(
            selectedRailDestination = selectedRailDestination,
            selectedRailFocusRequester = selectedRailFocusRequester,
            onDestinationChange = onDestinationChange,
        )

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            when (resolvedDestination) {
                SettingsDestination.INTERFACE -> LandscapeSectionPage(
                    title = "Interface",
                    subtitle = "Device profile and orientation.",
                ) {
                    InterfaceSettingsContent(
                        deviceProfile = deviceProfile,
                        orientationMode = orientationMode,
                        onSetOrientation = onSetOrientation,
                    )
                }

                SettingsDestination.CONTENT -> LandscapeSectionPage(
                    title = "Content",
                    subtitle = "Live organization, playlists and backup.",
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

                SettingsDestination.DOWNLOADS -> DownloadsSettingsScreen()

                SettingsDestination.ABOUT -> LandscapeSectionPage(
                    title = "About",
                    subtitle = "OwnPlay information.",
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
internal fun LandscapeSectionPage(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
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
