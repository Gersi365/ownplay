package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistDeleteDialogFocusContractTest {
    @Test
    fun `playlist delete confirmation focuses safe cancel on TV`() {
        val screen = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue(
            "The playlist delete confirmation must own an explicit Cancel focus requester.",
            "val deleteCancelFocusRequester = remember(summary.sourceId) { FocusRequester() }" in screen,
        )
        assertTrue(
            "Opening the delete confirmation must focus safe Cancel after composition.",
            "deleteConfirm && !deleteWorking" in screen &&
                "deleteCancelFocusRequester.requestFocus()" in screen &&
                ".focusRequester(deleteCancelFocusRequester)" in screen,
        )
    }

    @Test
    fun `playlist delete confirmation restores delete action after dismiss or failure`() {
        val screen = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue(
            "Dismiss paths must request restoration only after the modal closes and work is idle.",
            "!deleteConfirm && !deleteWorking && restoreDeleteActionFocus" in screen,
        )
        assertTrue(
            "Dismiss and failure paths must mark the Delete action for restoration.",
            "restoreDeleteActionFocus = true deleteConfirm = false" in screen,
        )
        assertTrue(
            "The remembered parent Delete action must explicitly receive focus again.",
            "focusedAction = TvPlaylistDetailAction.DELETE" in screen &&
                "actionFocusRequesters .getValue(TvPlaylistDetailAction.DELETE) .requestFocus()" in screen,
        )
        assertTrue(
            "Successful deletion must not request focus restoration on the page being removed.",
            "SourceMutationResult.Success -> { restoreDeleteActionFocus = false deleteConfirm = false deleteWorking = false onDeleted()" in screen,
        )
    }
}
