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
            "TV group management must keep a dedicated parent focus request.",
            "mutableStateOf<GroupManagerFocusRequest?>(GroupManagerFocusRequest.NewGroup)" in dialog,
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
            "Returning to the parent must request the remembered action rather than defaulting to the top.",
            "requester.requestFocus() parentFocusRequest = null" in dialog,
        )
        assertTrue(
            "A confirmed delete must restore a neighboring group when possible.",
            "val fallbackGroup = groups.getOrNull(deletedIndex + 1) ?: groups.getOrNull(deletedIndex - 1)" in dialog,
        )
    }

    @Test
    fun `custom group manager gives TV dialogs deterministic first focus`() {
        val dialog = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/CustomGroupManagerDialog.kt"),
        )

        assertTrue(
            "The parent TV dialog must focus the New group field on first entry.",
            "GroupManagerFocusRequest.NewGroup -> newGroupFocusRequester" in dialog &&
                ".focusRequester(newGroupFocusRequester)" in dialog,
        )
        assertTrue(
            "The nested rename dialog must focus its text field on TV.",
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
