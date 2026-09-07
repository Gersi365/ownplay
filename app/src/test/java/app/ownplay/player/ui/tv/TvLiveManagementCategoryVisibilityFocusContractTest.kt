package app.ownplay.player.ui.tv

import app.ownplay.player.testing.normalizedSource
import app.ownplay.player.testing.sourceText
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLiveManagementCategoryVisibilityFocusContractTest {
    @Test
    fun `tv category visibility restores the action after a mutation`() {
        val management = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "Category visibility must own a dedicated focus requester.",
            "val categoryVisibilityFocusRequester = remember { FocusRequester() }" in management,
        )
        assertTrue(
            "Restore state must start false so entering Live Management never auto-focuses category visibility.",
            "restoreCategoryVisibilityFocus by remember(selectedSourceId) { mutableStateOf(false) }" in management,
        )
        assertTrue(
            "Only TV category visibility mutations should arm focus restoration.",
            "if (isTelevision) restoreCategoryVisibilityFocus = true" in management,
        )
        assertTrue(
            "The Hide or Unhide category action must attach the dedicated focus requester.",
            "Modifier.focusRequester(categoryVisibilityFocusRequester)" in management,
        )
        assertTrue(
            "The mutation must continue disabling the action while persistence is in flight.",
            "enabled = !categoryMutationInFlight" in management,
        )
        assertTrue(
            "Focus restoration must wait until the mutation is no longer in flight.",
            "if (categoryMutationInFlight) return@LaunchedEffect" in management,
        )
        assertTrue(
            "After the mutation, TV must request the same category action and consume the restore request.",
            "categoryVisibilityFocusRequester.requestFocus()" in management &&
                "restoreCategoryVisibilityFocus = false" in management,
        )
    }

    @Test
    fun `category visibility focus restoration is safe when its target disappears`() {
        val management = normalizedSource(
            sourceText("src/main/java/app/ownplay/player/ui/LiveManagementScreen.kt"),
        )

        assertTrue(
            "A missing selected category must cancel a stale focus restore instead of targeting a removed control.",
            "if (selectedCategory == null) { restoreCategoryVisibilityFocus = false return@LaunchedEffect }" in management,
        )
        assertTrue(
            "Non-TV callers must clear any stale restore request rather than receiving automatic focus.",
            "if (!isTelevision) { restoreCategoryVisibilityFocus = false return@LaunchedEffect }" in management,
        )
    }
}
