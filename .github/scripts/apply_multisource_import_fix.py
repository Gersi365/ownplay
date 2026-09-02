from pathlib import Path
import re

RUNTIME = Path('app/src/main/java/app/ownplay/player/OwnPlayAppRuntime.kt')
SETTINGS = Path('app/src/main/java/app/ownplay/player/ui/PlaylistSettingsScreen.kt')
COORDINATOR = Path('app/src/main/java/app/ownplay/player/source/PendingImportCoordinator.kt')
TEST = Path('app/src/test/java/app/ownplay/player/source/PendingImportCoordinatorTest.kt')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected exactly one match, found {count}')
    return text.replace(old, new, 1)


runtime = RUNTIME.read_text()
runtime = replace_once(
    runtime,
    'import kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.CoroutineScope\n',
    'import kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.TimeoutCancellationException\nimport kotlinx.coroutines.async\nimport kotlinx.coroutines.coroutineScope\n',
    'coroutine imports',
)
runtime = replace_once(
    runtime,
    'import kotlinx.coroutines.sync.withLock\nimport kotlinx.coroutines.withContext\n\nclass OwnPlayAppRuntime(\n',
    'import kotlinx.coroutines.sync.withLock\nimport kotlinx.coroutines.withContext\nimport kotlinx.coroutines.withTimeout\n\nprivate const val PENDING_IMPORT_TIMEOUT_MILLIS = 120_000L\n\nclass OwnPlayAppRuntime(\n',
    'timeout import/constant',
)
runtime = replace_once(
    runtime,
    '    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)\n    private val refreshMutex = Mutex()\n',
    '    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)\n    private val refreshMutex = Mutex()\n    private val pendingImportCoordinator = PendingImportCoordinator(\n        scope = runtimeScope,\n        maxConcurrentImports = 2,\n        importSource = ::completePendingSource,\n    )\n',
    'pending coordinator property',
)
runtime = replace_once(
    runtime,
    '                    else -> completePendingSource(source.sourceId)\n',
    '                    else -> pendingImportCoordinator.schedule(source.sourceId)\n',
    'startup pending schedule',
)
runtime = replace_once(
    runtime,
    '                runtimeScope.launch { completePendingSource(result.sourceId) }\n',
    '                pendingImportCoordinator.schedule(result.sourceId)\n',
    'new source pending schedule',
)
runtime = replace_once(
    runtime,
    '    suspend fun retryPendingSource(sourceId: String) {\n        completePendingSource(sourceId)\n    }\n',
    '    suspend fun retryPendingSource(sourceId: String) {\n        pendingImportCoordinator.schedule(sourceId)\n    }\n',
    'retry scheduling',
)

new_complete = '''    private suspend fun completePendingSource(sourceId: String) = withContext(Dispatchers.IO) {
        try {
            withTimeout(PENDING_IMPORT_TIMEOUT_MILLIS) {
                val source = database.playlistSourceDao().getById(sourceId) ?: return@withTimeout
                if (source.enabled) return@withTimeout

                val existingCount = try {
                    database.providerCatalogDao().channelsForSource(sourceId)
                        .count { channel -> channel.availability != ChannelAvailability.REMOVED }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    0
                }
                val loadingState = SourceSyncState(
                    sourceId = sourceId,
                    sourceName = source.name,
                    stage = SourceSyncStage.LoadingChannels,
                    channelCount = existingCount,
                )
                _sourceSyncState.value = loadingState
                rememberSourceState(loadingState)
                markRefreshRunning(sourceId)

                val channelResult = refreshLiveCatalogInternal(
                    sourceId = sourceId,
                    sourceKind = source.sourceKind,
                )
                if (channelResult is SourceOnboardingResult.Failure) {
                    publishPendingImportFailure(
                        sourceId = sourceId,
                        failure = channelResult.reason.toSourceSyncFailure(),
                        errorCode = channelResult.reason.toString(),
                    )
                    return@withTimeout
                }
                channelResult as SourceOnboardingResult.Success

                // Delete is allowed while an import is in flight. Never recreate catalog state for a
                // source that disappeared while network work was still finishing.
                if (!sourceExists(sourceId)) return@withTimeout

                val now = System.currentTimeMillis()
                try {
                    database.withTransaction {
                        val currentSource = database.playlistSourceDao().getById(sourceId)
                            ?: return@withTransaction
                        database.playlistSourceDao().upsert(
                            currentSource.copy(
                                enabled = true,
                                updatedAtEpochMillis = now,
                            ),
                        )
                        val previous = database.refreshStateDao().get(sourceId)
                        database.refreshStateDao().upsert(
                            PlaylistRefreshStateEntity(
                                sourceId = sourceId,
                                generation = previous?.generation ?: now,
                                state = RefreshStates.SUCCEEDED,
                                lastAttemptAtEpochMillis = previous?.lastAttemptAtEpochMillis ?: now,
                                lastSuccessAtEpochMillis = now,
                                lastErrorCode = null,
                            ),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    publishPendingImportFailure(
                        sourceId = sourceId,
                        failure = SourceSyncFailure.Persistence,
                        errorCode = "persistence",
                    )
                    return@withTimeout
                }

                if (!sourceExists(sourceId)) return@withTimeout

                // Channel import is the readiness boundary. EPG/VOD/Series are best-effort follow-up
                // work and must never keep another playlist in the import queue.
                val epgState = SourceSyncState(
                    sourceId = sourceId,
                    sourceName = source.name,
                    stage = SourceSyncStage.LoadingEpg,
                    channelCount = channelResult.channelCount,
                )
                _sourceSyncState.value = epgState
                rememberSourceState(epgState)
                runtimeScope.launch {
                    loadEpgAfterChannels(
                        sourceId = sourceId,
                        sourceName = source.name,
                        channelCount = channelResult.channelCount,
                    )
                }
                if (source.sourceKind == SourceKinds.XTREAM) {
                    runtimeScope.launch { refreshXtreamMediaCatalogs(sourceId) }
                }
            }
        } catch (_: TimeoutCancellationException) {
            publishPendingImportFailure(
                sourceId = sourceId,
                failure = SourceSyncFailure.Source(SourceError.Timeout),
                errorCode = "import_timeout",
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            publishPendingImportFailure(
                sourceId = sourceId,
                failure = SourceSyncFailure.Unexpected,
                errorCode = "unexpected",
            )
        }
    }

    private suspend fun publishPendingImportFailure(
        sourceId: String,
        failure: SourceSyncFailure,
        errorCode: String,
    ) {
        val source = try {
            database.playlistSourceDao().getById(sourceId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return
        if (source.enabled) return

        val channelCount = try {
            database.providerCatalogDao().channelsForSource(sourceId)
                .count { channel -> channel.availability != ChannelAvailability.REMOVED }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            0
        }
        val failedState = SourceSyncState(
            sourceId = sourceId,
            sourceName = source.name,
            stage = SourceSyncStage.ChannelsFailed,
            channelCount = channelCount,
            failure = failure,
        )
        _sourceSyncState.value = failedState
        rememberSourceState(failedState)
        try {
            markRefreshFailed(sourceId, errorCode)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The visible failure is already published; refresh metadata is best-effort here.
        }
    }

'''
pattern = re.compile(
    r'    private suspend fun completePendingSource\(sourceId: String\) = withContext\(Dispatchers\.IO\) \{.*?\n    private suspend fun markRefreshRunning',
    re.S,
)
match = pattern.search(runtime)
if not match:
    raise RuntimeError('completePendingSource block not found')
runtime = runtime[:match.start()] + new_complete + '    private suspend fun markRefreshRunning' + runtime[match.end():]

old_xtream_fetch = '''        val categories = when (
            val loaded = xtreamClient.getLiveCategories(
                serverUrl = locator.serverUrl,
                credentials = credentials,
                allowCleartext = locator.allowCleartext,
            )
        ) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }
        val streams = when (
            val loaded = xtreamClient.getLiveStreams(
                serverUrl = locator.serverUrl,
                credentials = credentials,
                allowCleartext = locator.allowCleartext,
            )
        ) {
            is SourceResult.Success -> loaded.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(loaded.error),
                )
            }
        }

        val ingestResult = try {
'''
new_xtream_fetch = '''        // Categories and streams are independent after authentication. Fetch them together so a
        // slow provider cannot consume two consecutive 45-second network windows.
        val (categoriesResult, streamsResult) = coroutineScope {
            val categoriesDeferred = async {
                xtreamClient.getLiveCategories(
                    serverUrl = locator.serverUrl,
                    credentials = credentials,
                    allowCleartext = locator.allowCleartext,
                )
            }
            val streamsDeferred = async {
                xtreamClient.getLiveStreams(
                    serverUrl = locator.serverUrl,
                    credentials = credentials,
                    allowCleartext = locator.allowCleartext,
                )
            }
            categoriesDeferred.await() to streamsDeferred.await()
        }
        val categories = when (categoriesResult) {
            is SourceResult.Success -> categoriesResult.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(categoriesResult.error),
                )
            }
        }
        val streams = when (streamsResult) {
            is SourceResult.Success -> streamsResult.value
            is SourceResult.Failure -> {
                return SourceOnboardingResult.Failure(
                    SourceOnboardingFailure.SourceFailure(streamsResult.error),
                )
            }
        }

        // A delete can happen while network calls are in flight. Do not ingest orphaned rows.
        if (!sourceExists(sourceId)) {
            return SourceOnboardingResult.Failure(SourceOnboardingFailure.PersistenceFailure)
        }

        val ingestResult = try {
'''
runtime = replace_once(runtime, old_xtream_fetch, new_xtream_fetch, 'parallel Xtream catalog fetch')

new_delete = '''    suspend fun deleteSource(sourceId: String): SourceMutationResult {
        // Pending imports must be cancellable and deletable without waiting for an unrelated
        // refresh/import lock. Ready sources keep the existing refresh serialization.
        pendingImportCoordinator.cancel(sourceId)
        val isPending = try {
            database.playlistSourceDao().getById(sourceId)?.enabled == false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        val result = if (isPending) {
            sourceManagementService.delete(sourceId)
        } else {
            refreshMutex.withLock { sourceManagementService.delete(sourceId) }
        }
        if (result is SourceMutationResult.Success) {
            epgRepository.invalidateSource(sourceId)
            try {
                categoryVisibilityStore.clearSource(sourceId)
                categoryOrderStore.clearSource(sourceId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The source is already deleted. Stale source-scoped preferences are inert.
            }
            _sourceSyncStates.update { current -> current - sourceId }
            if (_sourceSyncState.value.sourceId == sourceId) {
                _sourceSyncState.value = SourceSyncState()
            }
        }
        return result
    }

'''
pattern = re.compile(
    r'    suspend fun deleteSource\(sourceId: String\): SourceMutationResult = refreshMutex\.withLock \{.*?\n    suspend fun ensureLiveCatalog',
    re.S,
)
match = pattern.search(runtime)
if not match:
    raise RuntimeError('deleteSource block not found')
runtime = runtime[:match.start()] + new_delete + '    suspend fun ensureLiveCatalog' + runtime[match.end():]

RUNTIME.write_text(runtime)

settings = SETTINGS.read_text()
settings = replace_once(
    settings,
    '                Text("${target.name} and its imported catalog will be removed from OwnPlay.")\n',
    '                Text(\n                    if (target.enabled) {\n                        "${target.name} and its imported catalog will be removed from OwnPlay."\n                    } else {\n                        "${target.name} will be removed and any pending import will be cancelled."\n                    },\n                )\n',
    'delete dialog pending copy',
)
settings = replace_once(
    settings,
    '                TextButton(onClick = onDelete, enabled = !syncing) { Text("Delete") }\n',
    '                TextButton(onClick = onDelete) { Text("Delete") }\n',
    'delete enabled while importing',
)
SETTINGS.write_text(settings)

COORDINATOR.parent.mkdir(parents=True, exist_ok=True)
COORDINATOR.write_text('''package app.ownplay.player.source

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
''')

TEST.parent.mkdir(parents=True, exist_ok=True)
TEST.write_text('''package app.ownplay.player.source

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingImportCoordinatorTest {
    @Test
    fun duplicateScheduleRunsSourceOnlyOnce() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val coordinator = PendingImportCoordinator(
            scope = this,
            maxConcurrentImports = 2,
        ) {
            calls.incrementAndGet()
            started.complete(Unit)
            release.await()
        }

        coordinator.schedule("source-a")
        coordinator.schedule("source-a")
        withTimeout(1_000) { started.await() }

        assertEquals(1, calls.get())
        assertTrue(coordinator.isActive("source-a"))
        release.complete(Unit)
        withTimeout(1_000) {
            while (coordinator.isActive("source-a")) delay(10)
        }
        assertFalse(coordinator.isActive("source-a"))
    }

    @Test
    fun concurrencyLimitLetsAnotherSourceRunWithoutGlobalSerialization() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val twoStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val coordinator = PendingImportCoordinator(
            scope = this,
            maxConcurrentImports = 2,
        ) {
            val now = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, now) }
            if (now == 2) twoStarted.complete(Unit)
            try {
                release.await()
            } finally {
                active.decrementAndGet()
            }
        }

        coordinator.schedule("source-a")
        coordinator.schedule("source-b")
        coordinator.schedule("source-c")
        withTimeout(1_000) { twoStarted.await() }

        assertEquals(2, maxActive.get())
        release.complete(Unit)
    }
}
''')

print('multisource import lifecycle transform applied')
