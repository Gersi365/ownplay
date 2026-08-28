package app.ownplay.player.ui

import app.ownplay.player.OwnPlayAppRuntime
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns source-submission work independently from the Add Playlist dialog lifecycle,
 * while remaining bounded to the OwnPlay runtime that initiated the submission.
 *
 * The dialog can close immediately after local validation while the network/catalog
 * import continues. Submissions are serialized per runtime so its single visible sync
 * state remains deterministic. Releasing a runtime cancels any unfinished submission
 * before that runtime closes its database and playback resources.
 */
internal object SourceSubmissionCoordinator {
    private data class RuntimeQueue(
        val scope: CoroutineScope,
        val mutex: Mutex = Mutex(),
    )

    private val queueLock = Any()
    private val queues = WeakHashMap<OwnPlayAppRuntime, RuntimeQueue>()

    fun submitXtream(
        runtime: OwnPlayAppRuntime,
        name: String,
        serverUrl: String,
        username: String,
        password: String,
        allowCleartext: Boolean,
    ) {
        submit(runtime) {
            runtime.addXtreamSource(
                name = name,
                serverUrl = serverUrl,
                username = username,
                password = password,
                allowCleartext = allowCleartext,
            )
        }
    }

    fun submitRemoteM3u(
        runtime: OwnPlayAppRuntime,
        name: String,
        playlistUrl: String,
        allowCleartext: Boolean = false,
    ) {
        submit(runtime) {
            runtime.addRemoteM3uSource(
                name = name,
                playlistUrl = playlistUrl,
                allowCleartext = allowCleartext,
            )
        }
    }

    fun submitLocalM3u(
        runtime: OwnPlayAppRuntime,
        name: String,
        documentUri: String,
        allowCleartext: Boolean = false,
    ) {
        submit(runtime) {
            runtime.addLocalM3uSource(
                name = name,
                documentUri = documentUri,
                allowCleartext = allowCleartext,
            )
        }
    }

    fun release(runtime: OwnPlayAppRuntime) {
        val queue = synchronized(queueLock) { queues.remove(runtime) }
        queue?.scope?.cancel()
    }

    private fun submit(
        runtime: OwnPlayAppRuntime,
        block: suspend () -> Unit,
    ) {
        val queue = queueFor(runtime)
        queue.scope.launch {
            queue.mutex.withLock {
                runSubmission(block)
            }
        }
    }

    private fun queueFor(runtime: OwnPlayAppRuntime): RuntimeQueue = synchronized(queueLock) {
        queues.getOrPut(runtime) {
            RuntimeQueue(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            )
        }
    }

    private suspend fun runSubmission(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Source runtime methods convert expected failures into SourceSyncState.
            // This guard only prevents an unexpected background exception from
            // crashing the process.
        }
    }
}
