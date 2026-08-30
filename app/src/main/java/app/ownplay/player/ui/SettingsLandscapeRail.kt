package app.ownplay.player.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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

    Surface(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
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
                detail = "Device profile & orientation",
                selected = selectedRailDestination == SettingsDestination.INTERFACE,
                focusRequester = selectedRailFocusRequester,
                onClick = { onDestinationChange(SettingsDestination.INTERFACE) },
            )
            SettingsRailItem(
                label = "Content",
                detail = "Live, playlists & backup",
                selected = selectedRailDestination == SettingsDestination.CONTENT,
                focusRequester = selectedRailFocusRequester,
                onClick = { onDestinationChange(SettingsDestination.CONTENT) },
            )
            if (!isTelevision) {
                SettingsRailItem(
                    label = "Downloads",
                    detail = "Offline movies & episodes",
                    selected = selectedRailDestination == SettingsDestination.DOWNLOADS,
                    focusRequester = selectedRailFocusRequester,
                    onClick = { onDestinationChange(SettingsDestination.DOWNLOADS) },
                )
            }
            SettingsRailItem(
                label = "About",
                detail = "OwnPlay information",
                selected = selectedRailDestination == SettingsDestination.ABOUT,
                focusRequester = selectedRailFocusRequester,
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
    onClick: () -> Unit,
) {
    var focused by remember(label) { mutableStateOf(false) }
    val emphasized = selected || focused

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.focusRequester(focusRequester) else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            focused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
