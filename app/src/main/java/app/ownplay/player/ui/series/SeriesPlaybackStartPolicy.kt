package app.ownplay.player.ui.series

import app.ownplay.player.series.SeriesEpisode

internal enum class SeriesPlaybackStartMode {
    RESUME,
    FROM_BEGINNING,
}

internal fun seriesPlaybackStartPosition(
    episode: SeriesEpisode,
    startMode: SeriesPlaybackStartMode,
): Long = when (startMode) {
    SeriesPlaybackStartMode.RESUME -> episode.positionMs
        ?.takeIf { it > 0L && !episode.progressCompleted }
        ?: 0L
    SeriesPlaybackStartMode.FROM_BEGINNING -> 0L
}

internal fun seriesEpisodeResumePercent(
    positionMs: Long?,
    durationMs: Long?,
): Int? {
    val position = positionMs ?: return null
    val duration = durationMs ?: return null
    if (position <= 0L || duration <= 0L) return null
    val boundedPosition = position.coerceAtMost(duration)
    return ((boundedPosition.toDouble() / duration.toDouble()) * 100.0)
        .toInt()
        .coerceIn(1, 99)
}

internal fun seriesEpisodeResumeLabel(episode: SeriesEpisode): String =
    seriesEpisodeResumePercent(episode.positionMs, episode.durationMs)
        ?.let { percent -> "Resume · $percent%" }
        ?: "Resume"

internal fun seriesEpisodePrimaryPlaybackLabel(
    episode: SeriesEpisode,
    offlineCopyAvailable: Boolean,
): String = when {
    offlineCopyAvailable && episode.resumeAvailable -> "Resume Offline"
    offlineCopyAvailable -> "Play Offline"
    episode.resumeAvailable -> "Resume"
    else -> "Play"
}

internal fun seriesEpisodeProgressLabel(episode: SeriesEpisode): String? = when {
    episode.resumeAvailable -> seriesEpisodeResumeLabel(episode)
    episode.progressCompleted -> "Watched"
    else -> null
}
