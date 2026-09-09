package app.ownplay.player.ui.tv

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvZeroRebuildArchitectureContractTest {
    @Test
    fun `tv source set is rebuilt from zero and does not retain legacy presentation tree`() {
        val tvUiRoot = File("src/tv/java/app/ownplay/player/ui")
        val target = File(tvUiRoot, "TargetOwnPlayApp.kt").readText()
        val rebuildRoot = File(tvUiRoot, "rebuild/RebuiltOwnPlayTvApp.kt").readText()
        val destinations = File(tvUiRoot, "rebuild/TvDestination.kt").readText()
        val theme = File(tvUiRoot, "rebuild/TvRebuildTheme.kt").readText()

        listOf(
            "TVOwnPlayApp.kt",
            "LiveRoute.kt",
            "home/TvHomeScreen.kt",
            "live/TvLiveChannelBrowser.kt",
            "live/TvLiveManagementScreen.kt",
            "live/TvLiveWorkspace.kt",
            "movies/TvMoviePlaybackRoute.kt",
            "movies/TvMoviesRoute.kt",
            "series/TvSeriesRoute.kt",
            "shell/TvDestination.kt",
            "shell/TvMediaShell.kt",
        ).forEach { legacyPath ->
            assertFalse(
                "Legacy TV presentation must not survive the from-zero rebuild: $legacyPath",
                File(tvUiRoot, legacyPath).exists(),
            )
        }

        assertTrue("Target must enter only the rebuilt TV root.", "RebuiltOwnPlayTvApp(" in target)
        assertFalse("Target must not reference the previous TV app.", "TVOwnPlayApp(" in target)

        listOf("HOME", "LIVE", "MOVIES", "SERIES", "SETTINGS").forEach { destination ->
            assertTrue("Missing rebuilt destination $destination", destination in destinations)
        }
        listOf("Home", "Live TV", "Movies", "Series", "Settings").forEach { label ->
            assertTrue("Missing rebuilt navigation label $label", "\"$label\"" in destinations)
        }

        assertTrue("Rebuilt home must use poster-style gateway surfaces.", "TvPosterGateway(" in rebuildRoot)
        assertTrue("Rebuild must use the new near-black background.", "0xFF09080E" in theme)
        assertTrue("Rebuild must use the restrained violet accent.", "0xFF8B5CF6" in theme)
        assertFalse("TV rebuild must not scale focused content.", ".scale(" in rebuildRoot)
        assertFalse("TV rebuild must not animate layout size on focus.", "animateContentSize" in rebuildRoot)
    }
}
