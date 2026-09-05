package app.ownplay.player.download

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineDownloadResilienceContractTest {
    @Test
    fun completedFilesUseSharedIntegrityVerification() {
        val repository = sourceText("src/main/java/app/ownplay/player/download/OfflineDownloadRepository.kt")
        val featureRuntime = sourceText("src/main/java/app/ownplay/player/download/OfflineDownloadFeatureRuntime.kt")

        assertTrue(repository.contains("suspend fun reconcileCompletedFiles(): Int"))
        assertTrue(repository.contains("OfflineDownloadFileIntegrity.verifiedBytes(applicationContext, row)"))
        assertTrue(repository.contains("OfflineDownloadFileIntegrity.verifiedBytes(applicationContext, existing)"))
        assertTrue(repository.contains("markCompletedFileFailed(row)"))
        assertTrue(featureRuntime.contains("repository.reconcileCompletedFiles()"))
        assertFalse(featureRuntime.contains("OfflineDownloadStorage.locationExists(applicationContext, row.localRelativePath)"))
    }

    @Test
    fun offlinePlaybackPresentationIsProcessScopedAndDisposeDoesNotStopPlayback() {
        val route = sourceText("src/main/java/app/ownplay/player/ui/library/UnifiedLibraryRoute.kt")
        val screen = sourceText("src/main/java/app/ownplay/player/ui/library/LibraryPlaybackScreen.kt")
        val session = sourceText("src/main/java/app/ownplay/player/ui/library/LibraryPlaybackPresentationSession.kt")

        assertTrue(route.contains("LibraryPlaybackPresentationSession.state.collectAsState()"))
        assertTrue(route.contains("LibraryPlaybackPresentationSession.show("))
        assertTrue(route.contains("LibraryPlaybackPresentationSession.clear()"))
        assertFalse(route.contains("var playbackSession by remember"))

        val disposal = screen
            .substringAfter("onDispose {")
            .substringBefore("PlaybackInteractionBridge.clearBackAction(backOwner)")
        assertFalse(disposal.contains("stopIfCurrent"))
        assertTrue(route.contains("runtime.playbackController.stop()"))

        assertTrue(session.contains("MutableStateFlow<LibraryPlaybackSession?>(null)"))
        assertFalse(session.contains("DataStore"))
    }

    private fun sourceText(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not locate source file: $relativePath")
    }
}
