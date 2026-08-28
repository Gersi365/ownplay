package app.ownplay.player.epg

internal class EpgRefreshGeneration {
    private val generations = mutableMapOf<String, Long>()

    @Synchronized
    fun snapshot(sourceId: String): Long = generations[sourceId] ?: 0L

    @Synchronized
    fun invalidate(sourceId: String) {
        generations[sourceId] = (generations[sourceId] ?: 0L) + 1L
    }

    @Synchronized
    fun isCurrent(sourceId: String, snapshot: Long): Boolean =
        (generations[sourceId] ?: 0L) == snapshot

    @Synchronized
    fun runIfCurrent(
        sourceId: String,
        snapshot: Long,
        action: () -> Unit,
    ): Boolean {
        if ((generations[sourceId] ?: 0L) != snapshot) return false
        action()
        return true
    }
}
