package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementSingleDoneContractTest {
    @Test
    fun `tv settings live management exposes one explicit done action`() {
        val tvSettings = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )
        val liveManagement = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )
        val liveBrowse = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "TV Settings must enter Live Management through the first-action focus path.",
            "focusFirstActionOnEntry = true" in tvSettings,
        )
        assertTrue(
            "The outer Done must be omitted for the TV first-action path while preserving legacy back-focus callers.",
            "if (!isTelevision || focusBackOnEntry)" in liveManagement,
        )
        assertTrue(
            "The embedded browse Done must still exit Live Management.",
            "onEditModeChanged = { editing -> if (!editing) onBack() }" in liveManagement,
        )
        assertTrue(
            "The browse header must retain the edit-mode Done action.",
            "Text(if (editState.isEditing) \"Done\" else \"Edit\")" in liveBrowse,
        )
    }
}
