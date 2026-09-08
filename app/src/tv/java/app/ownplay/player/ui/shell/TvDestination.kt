package app.ownplay.player.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class TvDestination(
    val label: String,
    val icon: ImageVector,
) {
    HOME(
        label = "Home",
        icon = Icons.Filled.Home,
    ),
    LIVE_TV(
        label = "Live TV",
        icon = Icons.Filled.LiveTv,
    ),
    MOVIES(
        label = "Movies",
        icon = Icons.Filled.Movie,
    ),
    SERIES(
        label = "Series",
        icon = Icons.Filled.Tv,
    ),
    SETTINGS(
        label = "Settings",
        icon = Icons.Filled.Settings,
    ),
}

internal val tvDestinations: List<TvDestination> = listOf(
    TvDestination.HOME,
    TvDestination.LIVE_TV,
    TvDestination.MOVIES,
    TvDestination.SERIES,
    TvDestination.SETTINGS,
)

internal val defaultTvDestination: TvDestination = TvDestination.HOME
