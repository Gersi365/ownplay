package app.ownplay.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCatalogStatePanelTest {
    @Test
    fun `visible content wins over refresh state`() {
        assertEquals(
            MediaCatalogPresentationState.CONTENT,
            mediaCatalogPresentationState(
                hasCatalogContent = true,
                visibleItemCount = 4,
                loading = true,
                failed = true,
            ),
        )
    }

    @Test
    fun `saved catalog with no visible matches stays an empty filter state`() {
        assertEquals(
            MediaCatalogPresentationState.EMPTY,
            mediaCatalogPresentationState(
                hasCatalogContent = true,
                visibleItemCount = 0,
                loading = false,
                failed = true,
            ),
        )
    }

    @Test
    fun `initial load is not presented as empty`() {
        assertEquals(
            MediaCatalogPresentationState.LOADING,
            mediaCatalogPresentationState(
                hasCatalogContent = false,
                visibleItemCount = 0,
                loading = true,
                failed = false,
            ),
        )
    }

    @Test
    fun `failed initial load is actionable error`() {
        assertEquals(
            MediaCatalogPresentationState.ERROR,
            mediaCatalogPresentationState(
                hasCatalogContent = false,
                visibleItemCount = 0,
                loading = false,
                failed = true,
            ),
        )
    }

    @Test
    fun `successful empty catalog remains empty`() {
        assertEquals(
            MediaCatalogPresentationState.EMPTY,
            mediaCatalogPresentationState(
                hasCatalogContent = false,
                visibleItemCount = 0,
                loading = false,
                failed = false,
            ),
        )
    }
}
