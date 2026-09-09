package app.ownplay.player.ui.rebuild

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TvNavigationRail(
    selected: TvDestination,
    requesters: Map<TvDestination, FocusRequester>,
    onSelected: (TvDestination) -> Unit,
    onEnterContent: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(196.dp)
            .fillMaxHeight(),
        color = Color(0xFF0D0B13),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 26.dp),
        ) {
            Text(
                text = "OwnPlay",
                color = RebuildTextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "TV",
                color = RebuildAccentSoft,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )

            Spacer(Modifier.height(34.dp))

            TvDestination.entries.forEach { destination ->
                TvRailItem(
                    destination = destination,
                    selected = destination == selected,
                    requester = requesters.getValue(destination),
                    onClick = { onSelected(destination) },
                    onEnterContent = onEnterContent,
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = "Your media. Your way.",
                color = RebuildTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun TvRailItem(
    destination: TvDestination,
    selected: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
    onEnterContent: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onEnterContent()
                    true
                } else {
                    false
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = if (active) RebuildPanelElevated else Color.Transparent,
        contentColor = if (active) RebuildTextPrimary else RebuildTextSecondary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = iconFor(destination),
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = if (active) RebuildAccentSoft else RebuildTextSecondary,
            )
            Text(
                text = destination.label,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

private fun iconFor(destination: TvDestination): ImageVector = when (destination) {
    TvDestination.HOME -> Icons.Outlined.Home
    TvDestination.LIVE -> Icons.Outlined.LiveTv
    TvDestination.MOVIES -> Icons.Outlined.Movie
    TvDestination.SERIES -> Icons.Outlined.VideoLibrary
    TvDestination.SETTINGS -> Icons.Outlined.Settings
}
