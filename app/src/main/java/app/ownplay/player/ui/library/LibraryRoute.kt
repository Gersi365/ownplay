package app.ownplay.player.ui.library

import androidx.compose.runtime.Composable
import app.ownplay.player.OwnPlayAppRuntime

@Composable
internal fun LibraryRoute(
    runtime: OwnPlayAppRuntime,
    sourceId: String?,
    sourceKind: String?,
    onOpenMovieDetails: (sourceId: String, movieId: String) -> Unit,
    onOpenSeriesDetails: (sourceId: String, seriesId: String) -> Unit,
    onFullscreenStateChanged: (Boolean) -> Unit,
) {
    UnifiedLibraryRoute(
        runtime = runtime,
        sourceId = sourceId,
        sourceKind = sourceKind,
        onOpenMovieDetails = onOpenMovieDetails,
        onOpenSeriesDetails = onOpenSeriesDetails,
        onFullscreenStateChanged = onFullscreenStateChanged,
    )
}
