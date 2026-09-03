package app.ownplay.player

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSyncPublicationContractTest {
    @Test
    fun sourceSpecificStatesArePublishedSynchronously() {
        val source = sourceText("src/main/java/app/ownplay/player/OwnPlayAppRuntime.kt")
        assertTrue(source.contains("private fun publishSourceState(state: SourceSyncState)"))
        assertFalse(source.contains("_sourceSyncState.collect(::rememberSourceState)"))

        val staleRefresh = source
            .substringAfter("private suspend fun refreshSourceIfStale(sourceId: String)")
            .substringBefore("private suspend fun completePendingSource(sourceId: String)")
        assertTrue(staleRefresh.contains("runReadyRefresh("))
        assertTrue(staleRefresh.contains("onlyIfStale = true"))
        assertFalse(staleRefresh.contains("refreshSource(sourceId)"))

        val lifecycle = source
            .substringAfter("private suspend fun runReadyRefresh(")
            .substringBefore("private suspend fun executeReadyRefreshLocked")
        assertTrue(lifecycle.contains("refreshMutex.withLock"))
        assertTrue(lifecycle.contains("if (onlyIfStale)"))
        assertTrue(lifecycle.contains("database.refreshStateDao().get(sourceId)"))
        assertTrue(lifecycle.contains("shouldRefreshSource("))
        assertTrue(lifecycle.contains("executeReadyRefreshLocked(sourceId)"))
        val freshnessIndex = lifecycle.indexOf("shouldRefreshSource(")
        val executionIndex = lifecycle.indexOf("executeReadyRefreshLocked(sourceId)")
        assertTrue(freshnessIndex >= 0 && freshnessIndex < executionIndex)

        val execution = source
            .substringAfter("private suspend fun executeReadyRefreshLocked(sourceId: String)")
            .substringBefore("suspend fun refreshSource(sourceId: String)")
        assertTrue(execution.contains("markRefreshRunning(sourceId)"))
        assertTrue(execution.contains("refreshSourcePipelineLocked(sourceId)"))
        assertTrue(execution.contains("markRefreshSucceeded(sourceId)"))
        assertTrue(execution.contains("markRefreshFailed(sourceId, outcome.failure.toString())"))
        assertFalse(execution.contains("refreshMutex.withLock"))
        val runningIndex = execution.indexOf("markRefreshRunning(sourceId)")
        val pipelineIndex = execution.indexOf("refreshSourcePipelineLocked(sourceId)")
        val successIndex = execution.indexOf("markRefreshSucceeded(sourceId)")
        assertTrue(runningIndex >= 0 && runningIndex < pipelineIndex)
        assertTrue(pipelineIndex >= 0 && pipelineIndex < successIndex)

        val manualRefresh = source
            .substringAfter("suspend fun refreshSource(sourceId: String)")
            .substringBefore("suspend fun refreshAllSources()")
        assertTrue(manualRefresh.contains("runReadyRefresh("))
        assertTrue(manualRefresh.contains("onlyIfStale = false"))

        val pipeline = source
            .substringAfter("private suspend fun refreshSourcePipelineLocked(")
            .substringBefore("private suspend fun refreshXtreamMediaCatalogs")
        assertTrue(pipeline.contains("publishSourceState("))
        assertTrue(pipeline.contains("ReadyRefreshOutcome.ChannelsFailed(failure)"))
        assertTrue(pipeline.contains("ReadyRefreshOutcome.Succeeded"))
        assertFalse(pipeline.contains("refreshMutex.withLock"))
        assertFalse(pipeline.contains("_sourceSyncState.value = SourceSyncState("))

        val unexpectedFailure = source
            .substringAfter("private suspend fun publishUnexpectedRefreshFailure(sourceId: String)")
            .substringBefore("private suspend fun loadEpgAfterChannels(")
        assertTrue(unexpectedFailure.contains("val current = _sourceSyncStates.value[sourceId]"))
        assertFalse(unexpectedFailure.contains("val current = _sourceSyncState.value"))

        val epg = source
            .substringAfter("private suspend fun loadEpgAfterChannels(")
            .substringBefore("suspend fun epgSnapshot(")
        assertTrue(epg.contains("publishSourceState("))
        assertFalse(epg.contains("_sourceSyncState.value = if (epg == null)"))
    }

    private fun sourceText(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not locate source file: $relativePath")
    }
}
