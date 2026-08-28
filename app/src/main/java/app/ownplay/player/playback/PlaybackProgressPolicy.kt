package app.ownplay.player.playback

internal data class NormalizedPlaybackProgress(
    val positionMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
)

internal object PlaybackProgressPolicy {
    private const val COMPLETION_FRACTION = 0.95

    fun normalize(
        positionMs: Long,
        durationMs: Long?,
        fallbackDurationMs: Long? = null,
    ): NormalizedPlaybackProgress {
        val normalizedPosition = positionMs.coerceAtLeast(0L)
        val normalizedDuration = durationMs?.takeIf { it > 0L }
            ?: fallbackDurationMs?.takeIf { it > 0L }
        val completed = normalizedDuration?.let { duration ->
            normalizedPosition >= (duration * COMPLETION_FRACTION).toLong()
        } ?: false
        return NormalizedPlaybackProgress(
            positionMs = normalizedPosition,
            durationMs = normalizedDuration,
            completed = completed,
        )
    }
}
