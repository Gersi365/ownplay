package app.ownplay.player.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOfflinePrimaryActionTest {
    @Test
    fun `offline mode uses local movie primary action only for verified copy`() {
        assertTrue(
            shouldUseOfflineMoviePrimaryAction(
                offlineOnly = true,
                verifiedOffline = true,
            ),
        )
        assertFalse(
            shouldUseOfflineMoviePrimaryAction(
                offlineOnly = true,
                verifiedOffline = false,
            ),
        )
        assertFalse(
            shouldUseOfflineMoviePrimaryAction(
                offlineOnly = false,
                verifiedOffline = true,
            ),
        )
    }

    @Test
    fun `offline mode opens offline series only when episodes are local`() {
        assertTrue(
            shouldUseOfflineSeriesPrimaryAction(
                offlineOnly = true,
                offlineEpisodeCount = 1,
            ),
        )
        assertTrue(
            shouldUseOfflineSeriesPrimaryAction(
                offlineOnly = true,
                offlineEpisodeCount = 3,
            ),
        )
        assertFalse(
            shouldUseOfflineSeriesPrimaryAction(
                offlineOnly = true,
                offlineEpisodeCount = 0,
            ),
        )
        assertFalse(
            shouldUseOfflineSeriesPrimaryAction(
                offlineOnly = false,
                offlineEpisodeCount = 2,
            ),
        )
    }
}
