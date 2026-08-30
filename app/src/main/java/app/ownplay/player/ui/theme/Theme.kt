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
    primary = Color(0xFF83B6FF),
    onPrimary = Color(0xFF071C38),
    primaryContainer = Color(0xFF172A43),
    onPrimaryContainer = Color(0xFFDCEAFF),
    secondary = Color(0xFF79D9C7),
    onSecondary = Color(0xFF062C27),
    // Keep navigation selection geometry visually stable: Material3 can still draw its
    // indicator, but it blends into the navigation surface and selection is expressed by tint.
    secondaryContainer = Color(0xFF0D1218),
    onSecondaryContainer = Color(0xFF83B6FF),
    tertiary = Color(0xFFF0BE7B),
    onTertiary = Color(0xFF352507),
    tertiaryContainer = Color(0xFF493617),
    onTertiaryContainer = Color(0xFFFFE4BE),
    background = Color(0xFF07090D),
    onBackground = Color(0xFFF1F4F8),
    surface = Color(0xFF0D1218),
    onSurface = Color(0xFFF1F4F8),
    surfaceVariant = Color(0xFF151C25),
    onSurfaceVariant = Color(0xFFAEB9C7),
    outline = Color(0xFF34404E),
    outlineVariant = Color(0xFF222C38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF501A1E),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val OwnPlayShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
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
