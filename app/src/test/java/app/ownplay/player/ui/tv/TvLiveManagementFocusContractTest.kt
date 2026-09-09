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
    fun `tv settings enters live management on the selected source`() {
        val tvSettings = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvSettingsScreen.kt"),
        )

        assertTrue(
            "TV Settings must route Live Management through the dedicated TV screen.",
            "TvLiveManagementScreen(" in tvSettings,
        )
        assertTrue(
            "TV Settings must request first-action focus for Live Management.",
            "focusFirstActionOnEntry = true" in tvSettings,
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
