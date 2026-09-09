package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementRemoteOrderEdgeFocusContractTest {
    @Test
    fun `tv remote order restores focus inward after reaching an edge`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "Top-edge completion must move focus to the still-enabled downward action.",
            "RemoteOrderEdge.TOP -> {" in source &&
                "remoteMoveDownFocusRequester.requestFocus()" in source,
        )
        assertTrue(
            "Bottom-edge completion must move focus to the still-enabled upward action.",
            "RemoteOrderEdge.BOTTOM -> {" in source &&
                "remoteMoveUpFocusRequester.requestFocus()" in source,
        )
        assertTrue(
            "The inward actions must own the explicit restore requesters.",
            "Modifier.focusRequester(remoteMoveUpFocusRequester)" in source &&
                "Modifier.focusRequester(remoteMoveDownFocusRequester)" in source,
        )
    }

    @Test
    fun `tv remote order arms edge restore only after a successful mutation`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "Mutation results must be reduced to an explicit success signal before focus restoration.",
            "val succeeded = if (useFavoriteOrder)" in source,
        )
        assertTrue(
            "Failure paths must not arm focus restoration.",
            "is FavoriteMutationResult.Failure -> { orderError = \"Could not save channel order.\" false }" in source &&
                "ManualOrderMutationResult.PersistenceFailure, -> { orderError = \"Could not save channel order.\" false }" in source,
        )
        assertTrue(
            "Only successful TV mutations with an edge target may create a pending restore request.",
            "if (succeeded && isTelevision && focusRestoreEdge != null)" in source &&
                "PendingRemoteOrderFocusRestore(" in source,
        )
        assertTrue(
            "A pending request must be tied to the channel that initiated the mutation.",
            "selectedChannelId != request.channelId" in source,
        )
    }

    @Test
    fun `tv remote order only requests edge restore when the invoked action can disable itself`() {
        val source = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "A one-step upward move only reaches the top from index one.",
            "focusRestoreEdge = if (selectedChannelIndex == 1) RemoteOrderEdge.TOP else null" in source,
        )
        assertTrue(
            "A one-step downward move only reaches the bottom from the penultimate index.",
            "selectedChannelIndex == state.channels.lastIndex - 1" in source &&
                "RemoteOrderEdge.BOTTOM" in source,
        )
        assertTrue(
            "Move-to-edge actions must always carry the corresponding edge restore target.",
            "focusRestoreEdge = RemoteOrderEdge.TOP" in source &&
                "focusRestoreEdge = RemoteOrderEdge.BOTTOM" in source,
        )
    }
}
