package app.ownplay.player.ui.tv

internal enum class TvRemoteActionKind(
    val cooldownMillis: Long,
) {
    STANDARD(400L),
    TRANSITION(900L),
}

internal class TvRemoteActionGuard {
    private var blockedUntilMillis = Long.MIN_VALUE

    @Synchronized
    fun tryAcquire(
        nowMillis: Long,
        kind: TvRemoteActionKind = TvRemoteActionKind.STANDARD,
    ): Boolean {
        if (nowMillis < blockedUntilMillis) return false
        blockedUntilMillis = saturatedAdd(nowMillis, kind.cooldownMillis)
        return true
    }

    @Synchronized
    fun extendBlock(
        nowMillis: Long,
        kind: TvRemoteActionKind = TvRemoteActionKind.TRANSITION,
    ) {
        blockedUntilMillis = maxOf(
            blockedUntilMillis,
            saturatedAdd(nowMillis, kind.cooldownMillis),
        )
    }

    private fun saturatedAdd(value: Long, increment: Long): Long =
        if (value > Long.MAX_VALUE - increment) Long.MAX_VALUE else value + increment
}
