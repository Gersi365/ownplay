package app.ownplay.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Schedules recoverable playlist imports without allowing one slow provider to block every source.
 * Duplicate schedules for the same source are collapsed into the existing job.
 */
internal class PendingImportCoordinator(
    private val scope: CoroutineScope,
    maxConcurrentImports: Int,
    private val importSource: suspend (String) -> Unit,
) {
    private val gate = Semaphore(maxConcurrentImports.also { require(it > 0) })
    private val lock = Any()
    private val jobs = mutableMapOf<String, Job>()

    fun schedule(sourceId: String) {
        val normalizedId = sourceId.trim()
        if (normalizedId.isEmpty()) return

        var jobToStart: Job? = null
        synchronized(lock) {
            if (jobs[normalizedId]?.isActive == true) return
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val thisJob = coroutineContext.job
                try {
                    gate.withPermit { importSource(normalizedId) }
                } finally {
                    synchronized(lock) {
                        if (jobs[normalizedId] === thisJob) {
                            jobs.remove(normalizedId)
                        }
                    }
                }
            }
            jobs[normalizedId] = job
            jobToStart = job
        }
        jobToStart?.start()
    }

    fun cancel(sourceId: String) {
        val normalizedId = sourceId.trim()
        if (normalizedId.isEmpty()) return
        val job = synchronized(lock) { jobs.remove(normalizedId) }
        job?.cancel()
    }

    fun isActive(sourceId: String): Boolean = synchronized(lock) {
        jobs[sourceId.trim()]?.isActive == true
    }
}
