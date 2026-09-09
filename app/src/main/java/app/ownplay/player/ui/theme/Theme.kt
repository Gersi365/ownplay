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
import app.ownplay.player.BuildConfig
import app.ownplay.player.personalization.AppDeviceProfile
import app.ownplay.player.ui.tv.TvRemoteIndication

private val OwnPlayDarkColors = darkColorScheme(
    primary = Color(0xFF9B7BFF),
    onPrimary = Color(0xFF170E2A),
    primaryContainer = Color(0xFF2A1F45),
    onPrimaryContainer = Color(0xFFE9DEFF),
    secondary = Color(0xFFB8A6E8),
    onSecondary = Color(0xFF201934),
    // Keep navigation selection geometry visually stable: Material3 can still draw its
    // indicator, but it blends into the navigation surface and selection is expressed by tint.
    secondaryContainer = Color(0xFF0D1016),
    onSecondaryContainer = Color(0xFF9B7BFF),
    tertiary = Color(0xFF78C8FF),
    onTertiary = Color(0xFF061D2C),
    tertiaryContainer = Color(0xFF163549),
    onTertiaryContainer = Color(0xFFD2EFFF),
    background = Color(0xFF080A0F),
    onBackground = Color(0xFFF3F1F7),
    surface = Color(0xFF0D1016),
    onSurface = Color(0xFFF3F1F7),
    surfaceVariant = Color(0xFF151922),
    onSurfaceVariant = Color(0xFFB8B4C2),
    outline = Color(0xFF373846),
    outlineVariant = Color(0xFF252833),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF501A1E),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * OwnPlay TV visual identity.
 *
 * This palette is intentionally isolated behind the TV build gate so the TV application keeps
 * one coherent dark-navy + cyan presentation from startup/loading through browse, Settings and
 * playback overlays without inheriting the older purple mobile visual language.
 */
private val OwnPlayTvDarkColors = darkColorScheme(
    primary = Color(0xFF18C7FF),
    onPrimary = Color(0xFF00131D),
    primaryContainer = Color(0xFF063A55),
    onPrimaryContainer = Color(0xFFE9F8FF),
    secondary = Color(0xFF73BFFF),
    onSecondary = Color(0xFF001B2A),
    secondaryContainer = Color(0xFF0B2A3D),
    onSecondaryContainer = Color(0xFFD5EEFF),
    tertiary = Color(0xFF7FE4FF),
    onTertiary = Color(0xFF00212C),
    tertiaryContainer = Color(0xFF103849),
    onTertiaryContainer = Color(0xFFD8F6FF),
    background = Color(0xFF030B14),
    onBackground = Color(0xFFF3FAFF),
    surface = Color(0xFF07131F),
    onSurface = Color(0xFFF3FAFF),
    surfaceVariant = Color(0xFF0B1C2A),
    onSurfaceVariant = Color(0xFF9FB7C8),
    outline = Color(0xFF315267),
    outlineVariant = Color(0xFF173247),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF501A1E),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val OwnPlayShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
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
        letterSpacing = 0.5.sp,
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
    val activeColors = if (BuildConfig.IS_TV_BUILD) OwnPlayTvDarkColors else OwnPlayDarkColors
    val tvIndication = remember(activeColors) {
        TvRemoteIndication(
            focusColor = activeColors.primary,
            pressedColor = Color.White,
        )
    }

    MaterialTheme(
        colorScheme = activeColors,
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
