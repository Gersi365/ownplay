package app.ownplay.player.ui.vod

import app.ownplay.player.vod.VodMovie
import app.ownplay.player.vod.VodMovieDetails

internal fun vodMovieWithCatalogProgress(
    selected: VodMovie,
    latest: VodMovie?,
): VodMovie {
    if (latest == null || latest.movieId != selected.movieId) return selected
    return selected.copy(
        positionMs = latest.positionMs,
        durationMs = latest.durationMs,
        progressCompleted = latest.progressCompleted,
        progressUpdatedAtEpochMillis = latest.progressUpdatedAtEpochMillis,
    )
}

internal fun vodDetailsWithMovieProgress(
    details: VodMovieDetails?,
    movie: VodMovie?,
): VodMovieDetails? {
    if (details == null || movie == null || details.movie.movieId != movie.movieId) return details
    return details.copy(
        movie = details.movie.copy(
            isFavorite = movie.isFavorite,
            positionMs = movie.positionMs,
            durationMs = movie.durationMs,
            progressCompleted = movie.progressCompleted,
            progressUpdatedAtEpochMillis = movie.progressUpdatedAtEpochMillis,
        ),
    )
}
