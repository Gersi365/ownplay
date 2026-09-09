package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementSingleDoneContractTest {
    @Test
    fun `tv live management has one logical top-level exit and nested done actions`() {
        val tvSettings = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )
        val liveManagement = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveManagementScreen.kt"),
        )

        assertTrue(
            "Shared TV Settings must enter Live Management through its injected presentation slot.",
            "liveManagementContent(" in tvSettings,
        )
        assertTrue(
            "The TV-native main page must expose an explicit Settings return action.",
            "label = \"‹ Settings\"" in liveManagement,
        )
        assertFalse(
            "The TV-native screen must not retain the generic edit-mode Done toggle.",
            "Text(if (editState.isEditing) \"Done\" else \"Edit\")" in liveManagement,
        )
        assertFalse(
            "The TV-native screen must not embed the generic LiveBrowseScreen exit path.",
            "LiveBrowseScreen(" in liveManagement,
        )
        assertTrue(
            "Dedicated nested pages must retain one logical Done action back to their parent page.",
            "label = \"Done\"" in liveManagement && "onClick = onDone" in liveManagement,
        )
        assertTrue(
            "Back from nested pages must return to Live Management rather than directly leaving Settings.",
            "BackHandler(enabled = page != TvLiveManagementPage.MAIN)" in liveManagement,
        )
    }
}
