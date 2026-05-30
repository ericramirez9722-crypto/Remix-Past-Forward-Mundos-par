package com.pastforward.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// High-end luxury palette: Black background, golden/yellow accents, deep dark surfaces
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF121212)
val AccentYellow = Color(0xFFFACC15)
val AccentYellowHover = Color(0xFFFDE047)
val PolaroidBackground = Color(0xFFF3F4F6)
val TextLight = Color(0xFFE5E5E5)
val TextMuted = Color(0xFF737373)

private val ColorScheme = darkColorScheme(
    primary = AccentYellow,
    secondary = Color(0xFF262626),
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color(0xFF000000),
    onBackground = TextLight,
    onSurface = TextLight
)

val CustomTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun PastForwardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = CustomTypography,
        content = content
    )
}
