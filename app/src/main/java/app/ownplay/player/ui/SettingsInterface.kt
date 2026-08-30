package app.ownplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.ownplay.player.persistence.PlaylistSourceSummary
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.personalization.AppOrientationMode

@Composable
internal fun PortraitSettingsMenu(
    deviceProfile: AppDeviceProfile?,
    orientationMode: AppOrientationMode,
    onSetDeviceProfile: (AppDeviceProfile) -> Unit,
    onSetOrientation: (AppOrientationMode) -> Unit,
    summaries: List<PlaylistSourceSummary>,
    onOpenLiveManagement: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenDownloads: () -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Device, content, downloads and app information.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CompactSettingsSection(
                title = "Interface",
                subtitle = "Device profile and orientation",
            ) {
                InterfaceSettingsContent(
                    deviceProfile = deviceProfile,
                    orientationMode = orientationMode,
                    onSetDeviceProfile = onSetDeviceProfile,
                    onSetOrientation = onSetOrientation,
                )
            }

            CompactSettingsSection(
                title = "Content",
                subtitle = "Live organization, playlists and backup",
            ) {
                ContentSettingsContent(
                    summaries = summaries,
                    onOpenLiveManagement = onOpenLiveManagement,
                    onOpenPlaylists = onOpenPlaylists,
                )
            }

            CompactSettingsSection(
                title = "Downloads",
                subtitle = "Offline movies and series episodes",
            ) {
                SettingsActionRow(
                    title = "Downloaded media",
                    detail = "Progress · retry · remove",
                    actionLabel = "Open",
                    onClick = onOpenDownloads,
                )
            }

            CompactSettingsSection(
                title = "About",
                subtitle = "OwnPlay information",
            ) {
                AboutSettingsContent()
            }
        }
    }
}

@Composable
internal fun InterfaceSettingsContent(
    deviceProfile: AppDeviceProfile?,
    orientationMode: AppOrientationMode,
    onSetDeviceProfile: (AppDeviceProfile) -> Unit,
    onSetOrientation: (AppOrientationMode) -> Unit,
) {
    SettingValueRow(
        label = "Device profile",
        value = deviceProfileLabel(deviceProfile),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrientationButton(
            label = "Smartphone",
            selected = deviceProfile == AppDeviceProfile.SMARTPHONE,
            onClick = { onSetDeviceProfile(AppDeviceProfile.SMARTPHONE) },
            modifier = Modifier.weight(1f),
        )
        OrientationButton(
            label = "Tablet",
            selected = deviceProfile == AppDeviceProfile.TABLET,
            onClick = { onSetDeviceProfile(AppDeviceProfile.TABLET) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OrientationButton(
            label = "Android TV",
            selected = deviceProfile == AppDeviceProfile.ANDROID_TV,
            onClick = { onSetDeviceProfile(AppDeviceProfile.ANDROID_TV) },
            modifier = Modifier.weight(1f),
        )
        OrientationButton(
            label = "TV Box",
            selected = deviceProfile == AppDeviceProfile.TV_BOX,
            onClick = { onSetDeviceProfile(AppDeviceProfile.TV_BOX) },
            modifier = Modifier.weight(1f),
        )
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    SettingValueRow(
        label = "App orientation",
        value = when (deviceProfile) {
            AppDeviceProfile.SMARTPHONE -> if (orientationMode == AppOrientationMode.LANDSCAPE) {
                "Landscape"
            } else {
                "Portrait"
            }
            null -> "Loading…"
            else -> "Landscape · fixed"
        },
    )
    if (deviceProfile == AppDeviceProfile.SMARTPHONE) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    }
    Text(
        text = when (deviceProfile) {
            AppDeviceProfile.SMARTPHONE ->
                "This controls the browsing layout. With an active Live preview, rotating to landscape can open the full player."
            null -> "Loading device profile…"
            AppDeviceProfile.TABLET ->
                "Tablet uses the Landscape touch layout."
            else ->
                "Android TV and TV Box use the Landscape D-pad/remote layout."
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun deviceProfileLabel(profile: AppDeviceProfile?): String = when (profile) {
    AppDeviceProfile.SMARTPHONE -> "Smartphone"
    AppDeviceProfile.TABLET -> "Tablet"
    AppDeviceProfile.ANDROID_TV -> "Android TV"
    AppDeviceProfile.TV_BOX -> "TV Box"
    null -> "Loading…"
}
