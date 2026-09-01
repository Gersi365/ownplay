package app.ownplay.player.ui

internal object DragAutoScrollPolicy {
    fun delta(
        pointerY: Float,
        viewportStart: Int,
        viewportEnd: Int,
        edgeSize: Float,
        step: Float,
    ): Float {
        if (
            !pointerY.isFinite() ||
            edgeSize <= 0f ||
            step <= 0f ||
            viewportEnd <= viewportStart
        ) {
            return 0f
        }

        return when {
            pointerY < viewportStart + edgeSize -> -step
            pointerY > viewportEnd - edgeSize -> step
            else -> 0f
        }
    }
}
