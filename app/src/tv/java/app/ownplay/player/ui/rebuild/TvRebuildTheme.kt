package app.ownplay.player.ui.rebuild

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val RebuildBackground = Color(0xFF09080E)
internal val RebuildPanel = Color(0xFF14111C)
internal val RebuildPanelElevated = Color(0xFF211C2D)
internal val RebuildAccent = Color(0xFF8B5CF6)
internal val RebuildAccentSoft = Color(0xFFB59BFF)
internal val RebuildTextPrimary = Color(0xFFF7F5FB)
internal val RebuildTextSecondary = Color(0xFFAAA5B4)

private val RebuildColorScheme = darkColorScheme(
    primary = RebuildAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B285C),
    onPrimaryContainer = Color.White,
    secondary = RebuildAccentSoft,
    onSecondary = Color(0xFF160E22),
    background = RebuildBackground,
    onBackground = RebuildTextPrimary,
    surface = RebuildPanel,
    onSurface = RebuildTextPrimary,
    surfaceVariant = RebuildPanelElevated,
    onSurfaceVariant = RebuildTextSecondary,
)

@Composable
internal fun OwnPlayRebuildTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RebuildColorScheme,
        content = content,
    )
}
