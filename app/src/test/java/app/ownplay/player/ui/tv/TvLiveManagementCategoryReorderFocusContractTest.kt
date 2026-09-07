package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementCategoryReorderFocusContractTest {
    @Test
    fun `tv category reorder restores focus to the Categories action`() {
        val management = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )
        val browse = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Live browse must expose an optional requester so existing callers keep their focus behavior.",
            "reorderCategoriesFocusRequester: FocusRequester? = null" in browse,
        )
        assertTrue(
            "The Categories button must attach the supplied requester without requiring it for shared callers.",
            "Modifier.focusRequester(reorderCategoriesFocusRequester)" in browse,
        )
        assertTrue(
            "TV Live Management must supply a dedicated category-reorder focus requester.",
            "val categoryReorderFocusRequester = remember { FocusRequester() }" in management &&
                "reorderCategoriesFocusRequester = if (isTelevision)" in management,
        )
        assertTrue(
            "Dismissing the sheet must arm restoration and restore Categories after the sheet leaves composition.",
            "if (isTelevision) restoreCategoryReorderFocus = true" in management &&
                "categoryReorderFocusRequester.requestFocus()" in management &&
                "restoreCategoryReorderFocus = false" in management,
        )
    }

    @Test
    fun `tv category reorder avoids a focusable no-op when reorder is impossible`() {
        val management = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )
        val browse = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/live/LiveBrowseScreen.kt"),
        )

        assertTrue(
            "Shared Live browse must preserve the existing enabled default for non-TV callers.",
            "reorderCategoriesEnabled: Boolean = true" in browse,
        )
        assertTrue(
            "The Categories action must honor the explicit enabled contract.",
            "enabled = reorderCategoriesEnabled" in browse,
        )
        assertTrue(
            "TV must require at least two categories before exposing reorder as enabled, while Mobile stays unchanged.",
            "reorderCategoriesEnabled = !isTelevision || state.categories.size > 1" in management,
        )
        assertTrue(
            "Restore state must start false so entering Live Management never auto-focuses Categories.",
            "restoreCategoryReorderFocus by remember(selectedSourceId) { mutableStateOf(false) }" in management,
        )
    }
}
