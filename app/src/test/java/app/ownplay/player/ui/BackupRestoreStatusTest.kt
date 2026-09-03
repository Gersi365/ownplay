package app.ownplay.player.ui

import app.ownplay.player.backup.BackupRestoreFailureReason
import app.ownplay.player.backup.BackupRestoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreStatusTest {
    @Test
    fun `successful restore reports skipped channel identities clearly`() {
        val status = restoreStatus(
            BackupRestoreResult.Success(
                appliedChannelRecords = 4,
                appliedGroups = 2,
                appliedMemberships = 6,
                unmatchedChannelIdentities = 3,
                ambiguousChannelIdentities = 1,
                omittedLogoOverrides = 0,
            ),
        )

        assertTrue(status.contains("Restore complete: 4 channel records, 2 groups, 6 memberships applied."))
        assertTrue(status.contains("Skipped channels: 3 unmatched, 1 ambiguous."))
    }

    @Test
    fun `unsupported backup version explains compatibility failure`() {
        assertEquals(
            "Restore rejected: this backup version is not supported by this OwnPlay version.",
            restoreStatus(
                BackupRestoreResult.Failure(BackupRestoreFailureReason.UNSUPPORTED_VERSION),
            ),
        )
    }

    @Test
    fun `persistence failure promises no partial restore`() {
        assertEquals(
            "Restore failed safely. No partial changes were kept.",
            restoreStatus(
                BackupRestoreResult.Failure(BackupRestoreFailureReason.PERSISTENCE_FAILURE),
            ),
        )
    }
}
