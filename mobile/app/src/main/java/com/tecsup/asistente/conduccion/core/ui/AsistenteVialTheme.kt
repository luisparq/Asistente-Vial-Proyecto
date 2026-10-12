package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F4EB),
    onPrimaryContainer = Color(0xFF073B36),
    secondary = Color(0xFF4B635E),
    secondaryContainer = Color(0xFFE2EEF1),
    onSecondaryContainer = Color(0xFF193544),
    tertiary = Color(0xFF416277),
    background = Color(0xFFF6F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EEEF),
    outline = Color(0xFF71858E),
    onSurfaceVariant = Color(0xFF445B66),
    onSurface = Color(0xFF142D3A),
    onBackground = Color(0xFF142D3A),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF73D7C6),
    primaryContainer = Color(0xFF124C43),
    onPrimaryContainer = Color(0xFFD5F4EB),
    secondary = Color(0xFFB2CCC5),
    tertiary = Color(0xFFA8CCE3),
    secondaryContainer = Color(0xFF263F4C),
    onSecondaryContainer = Color(0xFFD9EBF3),
    background = Color(0xFF101F29),
    surface = Color(0xFF101F29),
    onSurface = Color(0xFFE1EDF0),
    onBackground = Color(0xFFE1EDF0),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)
private val AppShapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp))

@Composable
fun AsistenteVialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
