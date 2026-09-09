package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementNestedDialogFocusContractTest {
    @Test
    fun `custom group manager restores the TV parent action after nested dialogs`() {
        val dialog = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/CustomGroupManagerDialog.kt"),
        )

        assertTrue(
            "TV group management must keep an explicit pending parent focus request only for nested returns.",
            "mutableStateOf<GroupManagerFocusRequest?>(null)" in dialog,
        )
        assertTrue(
            "Rename must remember the parent action that opened the nested dialog.",
            "action = GroupManagerAction.RENAME" in dialog &&
                "renameTarget = group" in dialog,
        )
        assertTrue(
            "Delete must remember the parent action that opened the nested dialog.",
            "action = GroupManagerAction.DELETE" in dialog &&
                "deleteTarget = group" in dialog,
        )
        assertTrue(
            "Returning to the parent must request the remembered action and consume that request.",
            "requester.requestFocus() parentFocusRequest = null" in dialog,
        )
        assertTrue(
            "A confirmed delete must restore a neighboring group when possible and otherwise use Done.",
            "val fallbackGroup = groups.getOrNull(deletedIndex + 1) ?: groups.getOrNull(deletedIndex - 1)" in dialog &&
                "?: GroupManagerFocusRequest.Done" in dialog,
        )
    }

    @Test
    fun `custom group manager keeps nested TV focus deterministic without forcing parent text entry`() {
        val dialog = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/CustomGroupManagerDialog.kt"),
        )

        assertTrue(
            "The parent dialog must retain a focusable Done fallback without forcing the New group text field.",
            "GroupManagerFocusRequest.Done -> doneFocusRequester" in dialog &&
                ".focusRequester(doneFocusRequester)" in dialog &&
                ".focusRequester(newGroupFocusRequester)" !in dialog,
        )
        assertTrue(
            "The nested rename dialog must focus its text field on TV after the user explicitly chooses Rename.",
            "if (isTelevision && groupToRename != null)" in dialog &&
                "renameInputFocusRequester.requestFocus()" in dialog &&
                ".focusRequester(renameInputFocusRequester)" in dialog,
        )
        assertTrue(
            "The destructive delete confirmation must keep its safe Cancel focus.",
            "deleteCancelFocusRequester.requestFocus()" in dialog &&
                ".focusRequester(deleteCancelFocusRequester)" in dialog,
        )
        assertTrue(
            "The group list must retain scroll state while a nested dialog owns focus.",
            "val groupListState = rememberLazyListState()" in dialog &&
                "state = groupListState" in dialog,
        )
    }
}
