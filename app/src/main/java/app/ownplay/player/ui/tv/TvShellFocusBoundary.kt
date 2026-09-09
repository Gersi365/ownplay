package app.ownplay.player.ui.tv

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * TV shell focus handoff contract shared by TV-visible presentations that live in different
 * source sets. A non-zero contentEntryGeneration represents an explicit D-pad Right handoff from
 * the currently active primary navigation destination. requestRailFocus is available only while
 * the primary rail is visible.
 */
internal data class TvShellFocusBoundary(
    val contentEntryGeneration: Int = 0,
    val railVisible: Boolean = false,
    val requestRailFocus: () -> Unit = {},
)

internal val LocalTvShellFocusBoundary = staticCompositionLocalOf {
    TvShellFocusBoundary()
}
