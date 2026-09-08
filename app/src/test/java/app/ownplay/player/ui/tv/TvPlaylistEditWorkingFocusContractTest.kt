package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaylistEditWorkingFocusContractTest {
    @Test
    fun `tv playlist edit disables all back activation while save is working`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue(
            "Remote Back must remain owned while the save mutation is working.",
            "BackHandler(enabled = working) { }" in source,
        )
        assertTrue(
            "The edit page must disable its visible Back control during the same mutation.",
            "onBack = onBack, backEnabled = !working" in source,
        )
        assertTrue(
            "The shared playlist scaffold must expose an enabled gate for its Back button.",
            "backEnabled: Boolean = true" in source &&
                "TextButton( onClick = onBack, enabled = backEnabled" in source,
        )
        assertTrue(
            "Save and Cancel must remain disabled while the mutation is working.",
            "Button( enabled = !working" in source &&
                "OutlinedButton( enabled = !working, onClick = onBack" in source,
        )
    }

    @Test
    fun `tv playlist edit restores save focus after a failed mutation`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/tv/TvPlaylistSettingsScreen.kt"),
        )

        assertTrue(
            "Save must own a stable focus requester for the edited source.",
            "val saveFocusRequester = remember(snapshot.sourceId) { FocusRequester() }" in source,
        )
        assertTrue(
            "Save activation must arm restoration before the control is disabled.",
            "restoreSaveFocus = true working = true" in source,
        )
        assertTrue(
            "Successful save navigation must consume the pending restore instead of targeting a disposed page.",
            "SourceMutationResult.Success -> { restoreSaveFocus = false onSaved() }" in source,
        )
        assertTrue(
            "Failed save must wait until working ends, then restore Save after layout settles.",
            "LaunchedEffect(snapshot.sourceId, working, restoreSaveFocus)" in source &&
                "if (working || !restoreSaveFocus) return@LaunchedEffect" in source &&
                "withFrameNanos { } saveFocusRequester.requestFocus() restoreSaveFocus = false" in source,
        )
        assertTrue(
            "The Save button must attach the dedicated focus requester.",
            "focusRequester(saveFocusRequester)" in source,
        )
    }
}
