package app.ownplay.player.download

import app.ownplay.player.persistence.download.DownloadStates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineSourceRemovalPolicyTest {
    @Test
    fun queuedAndDownloadingWorkRestartAfterDeleteFailure() {
        assertTrue(shouldRestartAfterSourceRemovalFailure(DownloadStates.QUEUED))
        assertTrue(shouldRestartAfterSourceRemovalFailure(DownloadStates.DOWNLOADING))
    }

    @Test
    fun pausedCompletedAndFailedWorkStayStoppedAfterDeleteFailure() {
        assertFalse(shouldRestartAfterSourceRemovalFailure(DownloadStates.PAUSED))
        assertFalse(shouldRestartAfterSourceRemovalFailure(DownloadStates.COMPLETED))
        assertFalse(shouldRestartAfterSourceRemovalFailure(DownloadStates.FAILED))
    }

    @Test
    fun rollbackDoesNotRestartDownloadPausedAfterSnapshot() {
        assertFalse(
            shouldRestartSourceRemovalRollback(
                wasActiveBeforeRemoval = true,
                currentState = DownloadStates.PAUSED,
            ),
        )
    }

    @Test
    fun rollbackRestartsOnlyWhenSnapshotAndCurrentStateAreActive() {
        assertTrue(
            shouldRestartSourceRemovalRollback(
                wasActiveBeforeRemoval = true,
                currentState = DownloadStates.QUEUED,
            ),
        )
        assertFalse(
            shouldRestartSourceRemovalRollback(
                wasActiveBeforeRemoval = false,
                currentState = DownloadStates.QUEUED,
            ),
        )
    }
}
