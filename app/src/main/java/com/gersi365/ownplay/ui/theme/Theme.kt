package com.gersi365.ownplay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OwnPlayDarkColors = darkColorScheme(
    primary = Color(0xFF8D80F6),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF101217),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF16191F),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF20242C),
    onSurfaceVariant = Color(0xFFB8BEC9),
)

@Composable
fun OwnPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OwnPlayDarkColors,
        content = content,
    )
}
