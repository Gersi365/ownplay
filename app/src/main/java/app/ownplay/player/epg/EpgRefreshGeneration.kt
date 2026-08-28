package app.ownplay.player.epg

import java.util.concurrent.ConcurrentHashMap

internal class EpgRefreshGeneration {
    private val generations = ConcurrentHashMap<String, Long>()

    fun snapshot(sourceId: String): Long = generations[sourceId] ?: 0L

    fun invalidate(sourceId: String) {
        generations.compute(sourceId) { _, current -> (current ?: 0L) + 1L }
    }

    fun isCurrent(sourceId: String, snapshot: Long): Boolean =
        (generations[sourceId] ?: 0L) == snapshot
}
