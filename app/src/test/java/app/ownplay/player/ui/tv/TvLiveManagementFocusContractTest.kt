package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementFocusContractTest {
    @Test
    fun `tv settings enters live management on the source selector`() {
        val tvSettings = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )
        val liveManagement = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "TV Settings must request first-action focus for Live Management.",
            "focusFirstActionOnEntry = true" in tvSettings,
        )
        assertTrue(
            "Live Management must request its source selector when an enabled source exists.",
            "focusFirstActionOnEntry && selectedSourceId != null -> firstActionFocusRequester.requestFocus()" in liveManagement,
        )
        assertTrue(
            "The first-action requester must be attached to the source selector.",
            "focusRequester = firstActionFocusRequester" in liveManagement,
        )
    }

    @Test
    fun `live management empty state falls back to back focus`() {
        val liveManagement = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "When no source exists, first-action entry focus must fall back to Back.",
            "focusFirstActionOnEntry || focusBackOnEntry -> backFocusRequester.requestFocus()" in liveManagement,
        )
    }
}
