package app.ownplay.player.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.ui.tv.TvRemoteIndication

private val OwnPlayDarkColors = darkColorScheme(
    primary = Color(0xFF69D6E3),
    onPrimary = Color(0xFF002F35),
    primaryContainer = Color(0xFF123F47),
    onPrimaryContainer = Color(0xFFB7F4FB),
    secondary = Color(0xFFA9B7FF),
    onSecondary = Color(0xFF17204C),
    secondaryContainer = Color(0xFF29325E),
    onSecondaryContainer = Color(0xFFE0E4FF),
    tertiary = Color(0xFFFFC66A),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E430A),
    onTertiaryContainer = Color(0xFFFFE1A8),
    background = Color(0xFF080B10),
    onBackground = Color(0xFFF2F5FA),
    surface = Color(0xFF10151D),
    onSurface = Color(0xFFF2F5FA),
    surfaceVariant = Color(0xFF19212C),
    onSurfaceVariant = Color(0xFFB8C3D1),
    outline = Color(0xFF3E4A59),
    outlineVariant = Color(0xFF293442),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF55161A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val OwnPlayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val OwnPlayTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)

@Composable
fun OwnPlayTheme(
    deviceProfile: AppDeviceProfile? = null,
    content: @Composable () -> Unit,
) {
    val actualConfiguration = LocalConfiguration.current
    val profiledConfiguration = remember(actualConfiguration, deviceProfile) {
        if (deviceProfile == null) {
            actualConfiguration
        } else {
            Configuration(actualConfiguration).apply {
                val requestedType = if (deviceProfile.usesDpad) {
                    Configuration.UI_MODE_TYPE_TELEVISION
                } else {
                    Configuration.UI_MODE_TYPE_NORMAL
                }
                uiMode =
                    (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or requestedType
            }
        }
    }
    val usesDpad = deviceProfile?.usesDpad == true
    val tvIndication = remember {
        TvRemoteIndication(
            focusColor = OwnPlayDarkColors.primary,
            pressedColor = Color.White,
        )
    }

    MaterialTheme(
        colorScheme = OwnPlayDarkColors,
        typography = OwnPlayTypography,
        shapes = OwnPlayShapes,
    ) {
        if (deviceProfile == null) {
            content()
        } else {
            CompositionLocalProvider(LocalConfiguration provides profiledConfiguration) {
                if (usesDpad) {
                    CompositionLocalProvider(LocalIndication provides tvIndication) {
                        content()
                    }
                } else {
                    content()
                }
            }
        }
    }
}
