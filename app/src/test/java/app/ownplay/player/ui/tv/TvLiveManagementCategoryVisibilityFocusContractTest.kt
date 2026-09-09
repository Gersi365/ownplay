package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementCategoryVisibilityFocusContractTest {
    private val management by lazy {
        normalizedSource(
            sourceText("src/tv/java/app/ownplay/player/ui/live/TvLiveManagementScreen.kt"),
        )
    }

    @Test
    fun `tv category visibility restores the action after a mutation`() {
        assertTrue(
            "Category visibility must own a dedicated focus requester.",
            "val categoryVisibilityFocusRequester = remember { FocusRequester() }" in management,
        )
        assertTrue(
            "The action must remain disabled while persistence is in flight.",
            "enabled = !categoryMutationInFlight" in management,
        )
        assertTrue(
            "TV must persist both hide and unhide through the established runtime path.",
            "runtime.unhideCategory(" in management && "runtime.hideCategory(" in management,
        )
        assertTrue(
            "Focus restoration must happen after the mutation leaves the in-flight state.",
            "categoryMutationInFlight = false withFrameNanos { } categoryVisibilityFocusRequester.requestFocus()" in management,
        )
    }

    @Test
    fun `category visibility action only exists for a selected category`() {
        assertTrue(
            "A missing selected category must remove the visibility action rather than target stale focus.",
            "if (selectedCategory != null)" in management,
        )
        assertTrue(
            "The visibility action must keep the dedicated requester when it exists.",
            "focusRequester = categoryVisibilityFocusRequester" in management,
        )
    }
}
