package app.ownplay.player.personalization

import kotlin.math.abs

data class VisibleChannelBounds(
    val channelId: String,
    val top: Float,
    val bottom: Float,
) {
    val center: Float
        get() = top + ((bottom - top) / 2f)
}

data class ChannelDragTarget(
    val anchorChannelId: String,
    val placement: ManualOrderPlacement,
)

object ChannelDragTargetResolver {
    fun resolve(
        pointerY: Float,
        draggedChannelId: String,
        visibleItems: List<VisibleChannelBounds>,
        validChannelIds: Set<String>? = null,
    ): ChannelDragTarget? {
        if (!pointerY.isFinite() || draggedChannelId.isBlank()) return null

        val candidates = visibleItems.filter { item ->
            item.channelId.isNotBlank() &&
                item.channelId != draggedChannelId &&
                (validChannelIds == null || item.channelId in validChannelIds) &&
                item.top.isFinite() &&
                item.bottom.isFinite() &&
                item.bottom >= item.top
        }
        if (candidates.isEmpty()) return null

        // Prefer the row that physically contains the pointer. This keeps the drop target stable
        // when adjacent rows have different heights. Only fall back to nearest center when the
        // pointer is in spacing outside every visible row.
        val target = candidates.firstOrNull { item ->
            pointerY >= item.top && pointerY <= item.bottom
        } ?: candidates.minByOrNull { item -> abs(pointerY - item.center) }
            ?: return null

        return ChannelDragTarget(
            anchorChannelId = target.channelId,
            placement = if (pointerY < target.center) {
                ManualOrderPlacement.BEFORE
            } else {
                ManualOrderPlacement.AFTER
            },
        )
    }
}
