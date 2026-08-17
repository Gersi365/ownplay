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
    ): ChannelDragTarget? {
        if (!pointerY.isFinite() || draggedChannelId.isBlank()) return null

        val target = visibleItems
            .asSequence()
            .filter { item ->
                item.channelId.isNotBlank() &&
                    item.channelId != draggedChannelId &&
                    item.top.isFinite() &&
                    item.bottom.isFinite() &&
                    item.bottom >= item.top
            }
            .minByOrNull { item -> abs(pointerY - item.center) }
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
