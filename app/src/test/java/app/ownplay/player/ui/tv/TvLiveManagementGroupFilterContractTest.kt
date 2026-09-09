package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementGroupFilterContractTest {
    @Test
    fun `deleting the active custom group clears its stale browse filter only on success`() {
        val source = normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveManagementScreen.kt"),
        )

        assertTrue(
            "TV Live Management must detect when the deleted group owns the active browse filter.",
            "clearDeletedActiveFilter = state.query.customGroupId == groupId" in source,
        )
        assertTrue(
            "The active group filter must only clear after a successful delete mutation.",
            "runtime.deleteCustomGroup(groupId) is CustomGroupMutationResult.Success" in source,
        )
        assertTrue(
            "A successfully deleted active group must return to the unfiltered group state.",
            "browseSession.selectCustomGroup(null)" in source,
        )
    }
}
