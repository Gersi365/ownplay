package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementFocusContractTest {
    private val managementSource by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveManagementScreen.kt"),
        )
    }

    @Test
    fun `tv settings enters injected live management on the selected source`() {
        val tvSettings = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )
        val sharedSettings = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/SettingsScreen.kt"),
        )
        val tvShell = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/TVOwnPlayApp.kt"),
        )

        assertTrue(
            "Shared TV Settings must invoke the injected Live Management content.",
            "liveManagementContent(" in tvSettings,
        )
        assertTrue(
            "SettingsScreen must pass its flavor injection into TV Settings.",
            "liveManagementContent = tvLiveManagementContent" in sharedSettings,
        )
        assertTrue(
            "The TV source set must inject the dedicated TV Live Management screen.",
            "TvLiveManagementScreen(" in tvShell,
        )
        assertTrue(
            "The injected TV screen must request first-action focus.",
            "focusFirstActionOnEntry = true" in tvShell,
        )
        assertTrue(
            "The TV screen must request its first action after composition.",
            "firstActionFocusRequester.requestFocus()" in managementSource,
        )
        assertTrue(
            "The selected source row must own the first-action requester.",
            "focusRequester = if (summary.sourceId == selectedSourceId)" in managementSource,
        )
    }

    @Test
    fun `live management empty state focuses its settings return action`() {
        assertTrue(
            "An empty TV Live Management state must keep a deterministic Back target.",
            "emptyBackFocusRequester.requestFocus()" in managementSource,
        )
        assertTrue(
            "The empty-state Settings action must own the fallback requester.",
            "focusRequester = emptyBackFocusRequester" in managementSource,
        )
    }

    @Test
    fun `nested tv management pages restore their originating action`() {
        assertTrue(
            "Nested TV pages must keep an explicit restoration target.",
            "var restoreTarget by remember(selectedSourceId)" in managementSource,
        )
        assertTrue(
            "Category ordering must restore the category-order action.",
            "TvLiveManagementRestoreTarget.CATEGORY_ORDER -> categoryOrderFocusRequester.requestFocus()" in managementSource,
        )
        assertTrue(
            "Custom groups must restore the custom-groups action.",
            "TvLiveManagementRestoreTarget.CUSTOM_GROUPS -> groupsFocusRequester.requestFocus()" in managementSource,
        )
        assertTrue(
            "Channel customization must restore the originating customize action.",
            "TvLiveManagementRestoreTarget.CHANNEL_CUSTOMIZATION -> customizeFocusRequester.requestFocus()" in managementSource,
        )
    }
}
