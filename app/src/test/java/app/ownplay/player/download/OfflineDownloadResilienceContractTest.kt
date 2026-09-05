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
        val worker = sourceText("src/main/java/app/ownplay/player/download/OfflineDownloadWorker.kt")

        assertTrue(repository.contains("suspend fun reconcileCompletedFiles(): Int"))
        assertTrue(repository.contains("OfflineDownloadFileIntegrity.verifiedBytes(applicationContext, row)"))
        assertTrue(repository.contains("OfflineDownloadFileIntegrity.verifiedBytes(applicationContext, existing)"))
        assertTrue(repository.contains("markCompletedFileFailed(row)"))
        assertTrue(featureRuntime.contains("repository.reconcileCompletedFiles()"))
        assertFalse(featureRuntime.contains("OfflineDownloadStorage.locationExists(applicationContext, row.localRelativePath)"))

        val completedWorkerBranch = worker
            .substringAfter("if (initialRow.state == DownloadStates.COMPLETED) {")
            .substringBefore("if (recoverFinalizedDownload(initialRow, dao))")
        assertTrue(completedWorkerBranch.contains("OfflineDownloadFileIntegrity.verifiedBytes(applicationContext, initialRow)"))
        assertTrue(completedWorkerBranch.contains("OfflineDownloadFileIntegrity.failureReason(applicationContext, initialRow)"))
        assertTrue(completedWorkerBranch.contains("markFailed("))
        assertFalse(completedWorkerBranch.contains("initialRow.state == DownloadStates.COMPLETED &&"))
    }

    @Test
    fun pauseAndWorkerCompletionUseActiveTransferCompareAndSet() {
        val persistence = sourceText("src/main/java/app/ownplay/player/persistence/download/DownloadPersistence.kt")
        val repository = sourceText("src/main/java/app/ownplay/player/download/OfflineDownloadRepository.kt")
        val worker = sourceText("src/main/java/app/ownplay/player/download/OfflineDownloadWorker.kt")

        assertTrue(persistence.contains("suspend fun updateActiveTransfer("))
        assertTrue(persistence.contains("AND state IN ('QUEUED', 'DOWNLOADING')"))

        val pauseBlock = repository
            .substringAfter("suspend fun pause(downloadId: String) {")
            .substringBefore("suspend fun resume(downloadId: String) {")
        assertTrue(pauseBlock.contains("dao.updateActiveTransfer("))
        assertTrue(pauseBlock.contains("if (paused > 0)"))
        assertFalse(pauseBlock.contains("dao.updateTransfer("))

        assertTrue(worker.contains("val markedDownloading = dao.updateActiveTransfer("))
        assertTrue(worker.contains("val progressUpdated = dao.updateActiveTransfer("))
        assertTrue(worker.contains("state = DownloadStates.COMPLETED"))
        assertTrue(worker.contains("if (completed == 0 && dao.getById(downloadId) == null)"))
        assertFalse(worker.contains("val cancellationState = if (row.state == DownloadStates.PAUSED)"))

        val retryHelper = worker
            .substringAfter("private suspend fun markQueuedForRetry(")
            .substringBefore("private suspend fun markFailed(")
        assertTrue(retryHelper.contains("dao.updateActiveTransfer("))
        assertFalse(retryHelper.contains("dao.updateTransfer("))
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
