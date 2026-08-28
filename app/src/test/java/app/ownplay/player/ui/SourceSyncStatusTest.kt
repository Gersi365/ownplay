package app.ownplay.player.ui

import app.ownplay.player.source.SourceError
import app.ownplay.player.source.SourceSyncFailure
import app.ownplay.player.source.SourceSyncStage
import app.ownplay.player.source.SourceSyncState
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceSyncStatusTest {
    @Test
    fun newSourceFailureDoesNotClaimExistingChannelsWereKept() {
        assertEquals(
            "Could not load channels. Authentication failed. Check your username and password.",
            sourceSyncStatus(
                SourceSyncState(
                    stage = SourceSyncStage.ChannelsFailed,
                    failure = SourceSyncFailure.Source(SourceError.AuthenticationFailed),
                ),
            ),
        )
    }

    @Test
    fun refreshFailurePreservesExistingCatalogMessageAndCause() {
        assertEquals(
            "Channel refresh failed. Network unavailable. Existing channels were kept.",
            sourceSyncStatus(
                SourceSyncState(
                    stage = SourceSyncStage.ChannelsFailed,
                    channelCount = 42,
                    failure = SourceSyncFailure.Source(SourceError.NetworkUnavailable),
                ),
            ),
        )
    }

    @Test
    fun operationalFailureHasUsefulExplanation() {
        assertEquals(
            "Could not load channels. Secure storage failed.",
            sourceSyncStatus(
                SourceSyncState(
                    stage = SourceSyncStage.ChannelsFailed,
                    failure = SourceSyncFailure.SecureStorage,
                ),
            ),
        )
    }
}
