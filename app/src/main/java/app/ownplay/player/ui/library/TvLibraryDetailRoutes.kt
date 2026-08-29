package app.ownplay.player.ui.library

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.ui.series.SeriesRoute
import app.ownplay.player.ui.vod.VodRoute

/**
 * Existing VOD/Series routes use landscape as a full catalog workspace and therefore expose their
 * historical section navigation rails. A TV item opened from the unified Library must instead stay
 * inside the Library hierarchy. Supplying a portrait-shaped presentation configuration selects the
 * existing detail-only branch while preserving the TV uiMode and all playback/download logic.
 */
@Composable
internal fun TvLibraryMovieDetailRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    movieId: String?,
    onMovieConsumed: () -> Unit,
    onBackToLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    TvLibraryDetailConfiguration {
        VodRoute(
            runtime = runtime,
            sourceId = sourceId,
            sourceKind = sourceKind,
            requestedMovieId = movieId,
            onRequestedMovieConsumed = onMovieConsumed,
            returnToLibraryOnDetailBack = true,
            onReturnToLibrary = onBackToLibrary,
            onOpenLive = {},
            onOpenSeries = {},
            onOpenSettings = onOpenSettings,
            onFullscreenStateChanged = onFullscreenStateChanged,
        )
    }
}

@Composable
internal fun TvLibrarySeriesDetailRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    seriesId: String?,
    onSeriesConsumed: () -> Unit,
    onBackToLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    TvLibraryDetailConfiguration {
        SeriesRoute(
            runtime = runtime,
            sourceId = sourceId,
            sourceKind = sourceKind,
            requestedSeriesId = seriesId,
            onRequestedSeriesConsumed = onSeriesConsumed,
            returnToLibraryOnDetailBack = true,
            onReturnToLibrary = onBackToLibrary,
            onOpenSettings = onOpenSettings,
            onFullscreenStateChanged = onFullscreenStateChanged,
        )
    }
}

@Composable
private fun TvLibraryDetailConfiguration(content: @Composable () -> Unit) {
    val current = LocalConfiguration.current
    val detailConfiguration = remember(current) {
        Configuration(current).apply {
            orientation = Configuration.ORIENTATION_PORTRAIT
        }
    }
    CompositionLocalProvider(LocalConfiguration provides detailConfiguration) {
        content()
    }
}
