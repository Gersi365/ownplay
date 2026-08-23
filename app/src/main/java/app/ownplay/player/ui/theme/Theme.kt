package app.ownplay.player.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OwnPlayDarkColors = darkColorScheme(
    primary = Color(0xFF9B8CFF),
    onPrimary = Color(0xFF15102F),
    primaryContainer = Color(0xFF302A5D),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFF73D7C5),
    onSecondary = Color(0xFF06201B),
    secondaryContainer = Color(0xFF173E38),
    onSecondaryContainer = Color(0xFFD3FFF5),
    tertiary = Color(0xFFFFB56B),
    onTertiary = Color(0xFF2A1600),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFF4F5F8),
    surface = Color(0xFF12151C),
    onSurface = Color(0xFFF4F5F8),
    surfaceVariant = Color(0xFF1D222C),
    onSurfaceVariant = Color(0xFFBAC1CE),
    outline = Color(0xFF474E5A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val OwnPlayShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun OwnPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OwnPlayDarkColors,
        shapes = OwnPlayShapes,
        content = content,
    )
}
