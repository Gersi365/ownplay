package app.ownplay.player.ui.library

import androidx.compose.runtime.Composable
import app.ownplay.player.OwnPlayAppRuntime
import app.ownplay.player.ui.series.SeriesRoute
import app.ownplay.player.ui.vod.VodRoute

/** Keeps TV details inside the Library hierarchy without exposing historical catalog navigation. */
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
) = VodRoute(
    runtime = runtime,
    sourceId = sourceId,
    sourceKind = sourceKind,
    requestedMovieId = movieId,
    onRequestedMovieConsumed = onMovieConsumed,
    returnToLibraryOnDetailBack = true,
    standaloneDetailPresentation = true,
    onReturnToLibrary = onBackToLibrary,
    onOpenLive = {},
    onOpenSeries = {},
    onOpenSettings = onOpenSettings,
    onFullscreenStateChanged = onFullscreenStateChanged,
)

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
) = SeriesRoute(
    runtime = runtime,
    sourceId = sourceId,
    sourceKind = sourceKind,
    requestedSeriesId = seriesId,
    onRequestedSeriesConsumed = onSeriesConsumed,
    returnToLibraryOnDetailBack = true,
    standaloneDetailPresentation = true,
    onReturnToLibrary = onBackToLibrary,
    onOpenSettings = onOpenSettings,
    onFullscreenStateChanged = onFullscreenStateChanged,
)
