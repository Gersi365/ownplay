package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementSourceSelectionContractTest {
    @Test
    fun `live management preserves selected source across summary metadata refresh`() {
        val liveManagement = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "Live Management must derive a stable source-id list from summaries.",
            "val sourceIds = summaries.map(PlaylistSourceSummary::sourceId)" in liveManagement,
        )
        assertTrue(
            "Selected source state must not be keyed to the full summary objects.",
            "var sourceId by remember { mutableStateOf(summaries.firstOrNull()?.sourceId) }" in liveManagement,
        )
        assertFalse(
            "Summary metadata refresh must not recreate selected-source state.",
            "remember(summaries)" in liveManagement,
        )
        assertTrue(
            "A still-existing selected source must survive refresh; fallback is only for a missing source id.",
            "val selectedSourceId = sourceId?.takeIf { it in sourceIds } ?: sourceIds.firstOrNull()" in liveManagement,
        )
    }

    @Test
    fun `live management repairs selection when selected source disappears`() {
        val liveManagement = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "Source removal must reconcile stored selection to the currently resolved source id.",
            "LaunchedEffect(sourceIds, selectedSourceId) { if (sourceId != selectedSourceId) { sourceId = selectedSourceId } }" in liveManagement,
        )
    }
}
