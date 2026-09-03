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

        val refresh = source
  .substringAfter("suspend fun refreshSource(sourceId: String)")
  .substringBefore("suspend fun refreshAllSources()")
        assertTrue(refresh.contains("val state = _sourceSyncStates.value[sourceId]"))
        assertFalse(refresh.contains("val state = _sourceSyncState.value"))

        val pipeline = source
  .substringAfter("private suspend fun refreshSourcePipeline(sourceId: String)")
  .substringBefore("private suspend fun refreshXtreamMediaCatalogs")
        assertTrue(pipeline.contains("publishSourceState("))
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
