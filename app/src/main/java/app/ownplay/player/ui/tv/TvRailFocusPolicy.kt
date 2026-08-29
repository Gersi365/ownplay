package app.ownplay.player.ui.tv

internal enum class TvRailFocusZone {
    RAIL,
    CONTENT,
    DETAIL,
}

internal enum class TvRailFocusAction {
    LEFT,
    RIGHT,
    BACK,
}

internal object TvRailFocusPolicy {
    fun destination(
        current: TvRailFocusZone,
        action: TvRailFocusAction,
        hasContent: Boolean,
        hasDetail: Boolean,
    ): TvRailFocusZone? = when (current) {
        TvRailFocusZone.RAIL -> when (action) {
            TvRailFocusAction.RIGHT -> TvRailFocusZone.CONTENT.takeIf { hasContent }
            TvRailFocusAction.LEFT,
            TvRailFocusAction.BACK,
            -> null
        }

        TvRailFocusZone.CONTENT -> when (action) {
            TvRailFocusAction.LEFT -> TvRailFocusZone.RAIL
            TvRailFocusAction.RIGHT -> TvRailFocusZone.DETAIL.takeIf { hasDetail }
            TvRailFocusAction.BACK -> null
        }

        TvRailFocusZone.DETAIL -> when (action) {
            TvRailFocusAction.LEFT,
            TvRailFocusAction.BACK,
            -> TvRailFocusZone.CONTENT.takeIf { hasContent }
            TvRailFocusAction.RIGHT -> null
        }
    }
}
