package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementClearSelectionFocusContractTest {
    @Test
    fun `tv clear selection restores focus to Select visible`() {
        val browse = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Clear-selection restore state must start false so entering Live Management never auto-focuses Select visible.",
            "restoreClearSelectionFocus by remember { mutableStateOf(false) }" in browse,
        )
        assertTrue(
            "Select visible must own a dedicated requester for the TV clear-selection fallback.",
            "val selectVisibleFocusRequester = remember { FocusRequester() }" in browse &&
                "Modifier.focusRequester(selectVisibleFocusRequester)" in browse,
        )
        assertTrue(
            "Only TV Clear activation must arm the focus fallback.",
            "if (isTelevision) restoreClearSelectionFocus = true" in browse,
        )
        assertTrue(
            "Clear must remain disabled when there is no selection.",
            "enabled = hasSelection" in browse,
        )
        assertTrue(
            "Focus restoration must wait until the selection is actually empty.",
            "if (selectedCount != 0) return@LaunchedEffect" in browse,
        )
        assertTrue(
            "After Clear removes its own focusability, TV must focus Select visible and consume the request.",
            "selectVisibleFocusRequester.requestFocus()" in browse &&
                "restoreClearSelectionFocus = false" in browse,
        )
    }

    @Test
    fun `clear selection focus restoration remains TV only`() {
        val browse = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Non-TV callers must clear any stale restore request instead of receiving automatic focus.",
            "if (!isTelevision) { restoreClearSelectionFocus = false return@LaunchedEffect }" in browse,
        )
        assertTrue(
            "Mobile Select visible must keep the existing modifier path without a forced requester.",
            "modifier = if (isTelevision) { Modifier.focusRequester(selectVisibleFocusRequester) } else { Modifier }" in browse,
        )
    }
}
