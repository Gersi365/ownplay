package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementParentDialogFocusContractTest {
    @Test
    fun `tv bulk edit restores focus to the action that opened a management dialog`() {
        val liveBrowse = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Bulk edit must receive the TV form-factor signal without changing shared defaults.",
            "isTelevision = isTelevision" in liveBrowse,
        )
        assertTrue(
            "Customize and Groups need stable focus requesters for parent-level restoration.",
            "val customizeFocusRequester = remember { FocusRequester() }" in liveBrowse &&
                "val groupsFocusRequester = remember { FocusRequester() }" in liveBrowse,
        )
        assertTrue(
            "Dismissed Customize must restore the Customize action only on TV.",
            "if (isTelevision) restoreDialogOrigin = BulkEditDialogOrigin.CUSTOMIZE" in liveBrowse &&
                "BulkEditDialogOrigin.CUSTOMIZE -> customizeFocusRequester.requestFocus()" in liveBrowse,
        )
        assertTrue(
            "Dismissed Groups must restore the Groups action only on TV.",
            "if (isTelevision) restoreDialogOrigin = BulkEditDialogOrigin.GROUPS" in liveBrowse &&
                "BulkEditDialogOrigin.GROUPS -> groupsFocusRequester.requestFocus()" in liveBrowse,
        )
    }

    @Test
    fun `tv parent dialog restoration does not introduce initial autofocus`() {
        val liveBrowse = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Parent restore state must start empty and only be armed by dialog dismissal.",
            "mutableStateOf<BulkEditDialogOrigin?>(null)" in liveBrowse,
        )
        assertTrue(
            "Restoration must wait until child dialogs are closed before requesting focus.",
            "if (!isTelevision || customizeTarget != null || showGroupManager) return@LaunchedEffect" in liveBrowse,
        )
        assertTrue(
            "A completed restore request must be consumed so it cannot re-focus repeatedly.",
            "restoreDialogOrigin = null" in liveBrowse,
        )
    }
}
