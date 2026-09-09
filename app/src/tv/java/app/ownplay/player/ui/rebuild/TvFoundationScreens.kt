package app.ownplay.player.ui.rebuild

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableIntStateOf
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
import app.ownplay.player.persistence.PlaylistSourceSummary

@Composable
internal fun TvHomeFoundationScreen(
    sources: List<PlaylistSourceSummary>,
    entryRequester: FocusRequester,
    onNavigate: (TvDestination) -> Unit,
    onReturnToRail: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 34.dp),
    ) {
        Text(
            text = "OwnPlay",
            color = RebuildTextPrimary,
            fontSize = 38.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (sources.isEmpty()) {
                "Connect a playlist to start building your media home."
            } else {
                "${sources.count { it.enabled }} active source(s) · ${sources.sumOf { it.channelCount }} live channels"
            },
            color = RebuildTextSecondary,
            fontSize = 16.sp,
        )

        Spacer(Modifier.height(34.dp))
        Text(
            text = "Browse",
            color = RebuildTextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TvPosterGateway(
                title = "Live TV",
                caption = "Channels & EPG",
                icon = Icons.Outlined.LiveTv,
                modifier = Modifier.focusRequester(entryRequester),
                returnToRailOnLeft = true,
                onClick = { onNavigate(TvDestination.LIVE) },
                onReturnToRail = onReturnToRail,
            )
            TvPosterGateway(
                title = "Movies",
                caption = "Your VOD catalog",
                icon = Icons.Outlined.Movie,
                onClick = { onNavigate(TvDestination.MOVIES) },
                onReturnToRail = onReturnToRail,
            )
            TvPosterGateway(
                title = "Series",
                caption = "Shows & episodes",
                icon = Icons.Outlined.VideoLibrary,
                onClick = { onNavigate(TvDestination.SERIES) },
                onReturnToRail = onReturnToRail,
            )
            TvPosterGateway(
                title = "Settings",
                caption = "Sources & app",
                icon = Icons.Outlined.Settings,
                onClick = { onNavigate(TvDestination.SETTINGS) },
                onReturnToRail = onReturnToRail,
            )
        }

        Spacer(Modifier.height(34.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            color = RebuildPanel,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "A new OwnPlay TV",
                    color = RebuildTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Media-first navigation built independently from the previous TV presentation tree.",
                    color = RebuildTextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
internal fun TvCatalogFoundationScreen(
    destination: TvDestination,
    sources: List<PlaylistSourceSummary>,
    entryRequester: FocusRequester,
    onOpenSettings: () -> Unit,
    onReturnToRail: () -> Unit,
) {
    val (title, subtitle, icon) = when (destination) {
        TvDestination.LIVE -> Triple(
            "Live TV",
            "Channels, categories and EPG in a single TV-first workspace.",
            Icons.Outlined.LiveTv,
        )
        TvDestination.MOVIES -> Triple(
            "Movies",
            "Poster-first browsing with a dedicated metadata page.",
            Icons.Outlined.Movie,
        )
        TvDestination.SERIES -> Triple(
            "Series",
            "Series, seasons and episodes without legacy Library chrome.",
            Icons.Outlined.VideoLibrary,
        )
        else -> error("Catalog foundation is only valid for Live, Movies or Series")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 34.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RebuildAccentSoft,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                color = RebuildTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            color = RebuildTextSecondary,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(30.dp))

        Surface(
            color = RebuildPanel,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (sources.isEmpty()) "No media source connected" else "$title catalog",
                    color = RebuildTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (sources.isEmpty()) {
                        "Add an Xtream or M3U source from Settings."
                    } else {
                        "This new surface will be wired directly to the existing provider runtime without restoring the previous TV screen."
                    },
                    color = RebuildTextSecondary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(20.dp))
                TvWideAction(
                    label = if (sources.isEmpty()) "Open Settings" else "Manage sources",
                    modifier = Modifier.focusRequester(entryRequester),
                    onClick = onOpenSettings,
                    onReturnToRail = onReturnToRail,
                )
            }
        }
    }
}

@Composable
internal fun TvSettingsFoundationScreen(
    sources: List<PlaylistSourceSummary>,
    entryRequester: FocusRequester,
    onReturnToRail: () -> Unit,
) {
    val entries = listOf(
        "Playlists" to "${sources.size} configured source(s)",
        "Live Management" to "Categories, channels, groups and ordering",
        "Backup & Restore" to "Supported local personalization",
        "About" to "OwnPlay TV",
    )
    var selectedIndex by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 34.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.width(430.dp)) {
            Text(
                text = "Settings",
                color = RebuildTextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Simple TV-first configuration.",
                color = RebuildTextSecondary,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(26.dp))

            entries.forEachIndexed { index, (title, detail) ->
                TvSettingsRow(
                    title = title,
                    detail = detail,
                    selected = selectedIndex == index,
                    modifier = if (index == 0) Modifier.focusRequester(entryRequester) else Modifier,
                    onClick = { selectedIndex = index },
                    onReturnToRail = onReturnToRail,
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            color = RebuildPanel,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = entries[selectedIndex].first,
                    color = RebuildTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = entries[selectedIndex].second,
                    color = RebuildTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun TvPosterGateway(
    title: String,
    caption: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    returnToRailOnLeft: Boolean = false,
    onClick: () -> Unit,
    onReturnToRail: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(190.dp)
            .height(248.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    returnToRailOnLeft &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft
                ) {
                    onReturnToRail()
                    true
                } else {
                    false
                }
            },
        shape = MaterialTheme.shapes.large,
        color = if (focused) Color(0xFF35274E) else RebuildPanel,
        contentColor = RebuildTextPrimary,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) RebuildAccentSoft else RebuildTextSecondary,
                modifier = Modifier.size(34.dp),
            )
            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = caption,
                    color = RebuildTextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun TvWideAction(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onReturnToRail: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(210.dp)
            .height(58.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onReturnToRail()
                    true
                } else {
                    false
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = if (focused) RebuildAccent else RebuildPanelElevated,
        contentColor = Color.White,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TvSettingsRow(
    title: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onReturnToRail: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onReturnToRail()
                    true
                } else {
                    false
                }
            },
        shape = MaterialTheme.shapes.medium,
        color = when {
            focused -> Color(0xFF35274E)
            selected -> RebuildPanelElevated
            else -> RebuildPanel
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = RebuildTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = detail,
                color = RebuildTextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}
