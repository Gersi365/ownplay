package app.ownplay.player.ui

import app.ownplay.player.live.LiveCategory
import app.ownplay.player.personalization.ManualOrderPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryReorderPolicyTest {
    @Test
    fun categoryCanMoveBeforeAnchor() {
        val result = moveRelative(
            categories = categories(),
            draggedKey = "kids",
            anchorKey = "sports",
            placement = ManualOrderPlacement.BEFORE,
        )

        assertEquals(listOf("news", "kids", "sports"), result.map { it.providerCategoryKey })
    }

    @Test
    fun categoryCanMoveAfterAnchor() {
        val result = moveRelative(
            categories = categories(),
            draggedKey = "news",
            anchorKey = "sports",
            placement = ManualOrderPlacement.AFTER,
        )

        assertEquals(listOf("sports", "news", "kids"), result.map { it.providerCategoryKey })
    }

    @Test
    fun categoryCanMoveToFirstPosition() {
        val result = moveRelative(
            categories = categories(),
            draggedKey = "kids",
            anchorKey = "news",
            placement = ManualOrderPlacement.BEFORE,
        )

        assertEquals(listOf("kids", "news", "sports"), result.map { it.providerCategoryKey })
    }

    @Test
    fun categoryCanMoveToLastPosition() {
        val result = moveRelative(
            categories = categories(),
            draggedKey = "news",
            anchorKey = "kids",
            placement = ManualOrderPlacement.AFTER,
        )

        assertEquals(listOf("sports", "kids", "news"), result.map { it.providerCategoryKey })
    }

    @Test
    fun unknownAnchorLeavesOrderUnchanged() {
        val original = categories()
        val result = moveRelative(
            categories = original,
            draggedKey = "news",
            anchorKey = "missing",
            placement = ManualOrderPlacement.AFTER,
        )

        assertEquals(original, result)
    }

    private fun categories() = listOf(
        LiveCategory("news", "News", 0),
        LiveCategory("sports", "Sports", 1),
        LiveCategory("kids", "Kids", 2),
    )
}
