package app.ownplay.player.playback

enum class PlaybackNetworkState {
    AVAILABLE,
    UNAVAILABLE,
}

data class PlaybackRetryPolicy(
    val maxAutomaticAttempts: Int = 2,
    val initialDelayMillis: Long = 750L,
    val maxDelayMillis: Long = 3_000L,
) {
    init {
        require(maxAutomaticAttempts >= 0) { "Automatic retry attempts must not be negative" }
        require(initialDelayMillis >= 0L) { "Initial retry delay must not be negative" }
        require(maxDelayMillis >= initialDelayMillis) {
            "Maximum retry delay must be greater than or equal to initial delay"
        }
    }

    fun delayBeforeAttempt(attempt: Int): Long {
        require(attempt > 0) { "Retry attempt must be positive" }
        var delayMillis = initialDelayMillis
        repeat(attempt - 1) {
            if (delayMillis < maxDelayMillis) {
                delayMillis = if (delayMillis > maxDelayMillis / 2L) {
                    maxDelayMillis
                } else {
                    (delayMillis * 2L).coerceAtMost(maxDelayMillis)
                }
            }
        }
        return delayMillis
    }
}
