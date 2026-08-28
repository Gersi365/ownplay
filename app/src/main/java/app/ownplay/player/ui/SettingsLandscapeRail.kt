package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LandscapeSettingsRail(
    selectedRailDestination: SettingsDestination,
    selectedRailFocusRequester: FocusRequester,
    onDestinationChange: (SettingsDestination) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val railWidth = if (isTelevision) 272.dp else 220.dp
    val railPadding = if (isTelevision) 14.dp else 10.dp
    val itemSpacing = if (isTelevision) 6.dp else 4.dp

    Surface(
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(railPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (isTelevision) 10.dp else 8.dp,
                    vertical = if (isTelevision) 10.dp else 8.dp,
                ),
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
                detail = "Device profile & layout",
                selected = selectedRailDestination == SettingsDestination.INTERFACE,
                focusRequester = selectedRailFocusRequester,
                isTelevision = isTelevision,
                onClick = { onDestinationChange(SettingsDestination.INTERFACE) },
            )
            SettingsRailItem(
                label = "Content",
                detail = "Live & playlists",
                selected = selectedRailDestination == SettingsDestination.CONTENT,
                focusRequester = selectedRailFocusRequester,
                isTelevision = isTelevision,
                onClick = { onDestinationChange(SettingsDestination.CONTENT) },
            )
            SettingsRailItem(
                label = "Downloads",
                detail = "Offline movies & episodes",
                selected = selectedRailDestination == SettingsDestination.DOWNLOADS,
                focusRequester = selectedRailFocusRequester,
                isTelevision = isTelevision,
                onClick = { onDestinationChange(SettingsDestination.DOWNLOADS) },
            )
            SettingsRailItem(
                label = "Playback",
                detail = "Preview & fullscreen",
                selected = selectedRailDestination == SettingsDestination.PLAYBACK,
                focusRequester = selectedRailFocusRequester,
                isTelevision = isTelevision,
                onClick = { onDestinationChange(SettingsDestination.PLAYBACK) },
            )
            SettingsRailItem(
                label = "Refresh",
                detail = "Source updates",
                selected = selectedRailDestination == SettingsDestination.REFRESH,
                focusRequester = selectedRailFocusRequester,
                isTelevision = isTelevision,
                onClick = { onDestinationChange(SettingsDestination.REFRESH) },
            )
            SettingsRailItem(
                label = "About",
                detail = "OwnPlay information",
                selected = selectedRailDestination == SettingsDestination.ABOUT,
                focusRequester = selectedRailFocusRequester,
                isTelevision = isTelevision,
                onClick = { onDestinationChange(SettingsDestination.ABOUT) },
            )
        }
    }
}

@Composable
internal fun SettingsRailItem(
    label: String,
    detail: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    isTelevision: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.focusRequester(focusRequester) else Modifier,
            )
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (isTelevision) 14.dp else 10.dp,
                vertical = if (isTelevision) 12.dp else 9.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isTelevision) 3.dp else 1.dp),
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
