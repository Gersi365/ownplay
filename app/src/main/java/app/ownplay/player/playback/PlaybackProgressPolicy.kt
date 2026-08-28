package app.ownplay.player.playback

internal data class NormalizedPlaybackProgress(
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
)

internal fun normalizePlaybackProgress(
    positionMs: Long,
    reportedDurationMs: Long?,
    existingDurationMs: Long? = null,
): NormalizedPlaybackProgress {
    val position = positionMs.coerceAtLeast(0L)
    val duration = reportedDurationMs?.takeIf { it > 0L }
        ?: existingDurationMs?.takeIf { it > 0L }
    val completed = duration?.let { knownDuration ->
        position >= (knownDuration * 0.95).toLong()
    } ?: false
    return NormalizedPlaybackProgress(
        positionMs = position,
        durationMs = duration,
        completed = completed,
    )
}
