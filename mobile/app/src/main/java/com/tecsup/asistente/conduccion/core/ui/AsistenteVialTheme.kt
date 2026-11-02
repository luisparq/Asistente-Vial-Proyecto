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
    primary = Color(0xFFAA4224),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCB),
    onPrimaryContainer = Color(0xFF55200F),
    secondary = Color(0xFF5E6250),
    secondaryContainer = Color(0xFFE7E8DA),
    onSecondaryContainer = Color(0xFF34382C),
    tertiary = Color(0xFF725D87),
    background = Color(0xFFF5F2EB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECE7DD),
    surfaceContainer = Color(0xFFF0ECE4),
    surfaceContainerLow = Color(0xFFF9F6F0),
    surfaceContainerHigh = Color(0xFFEAE5DB),
    outline = Color(0xFF82796F),
    outlineVariant = Color(0xFFDFD8CB),
    onSurfaceVariant = Color(0xFF6C635B),
    onSurface = Color(0xFF292721),
    onBackground = Color(0xFF292721),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB491),
    onPrimary = Color(0xFF55200F),
    primaryContainer = Color(0xFF71341E),
    onPrimaryContainer = Color(0xFFFFDCCB),
    secondary = Color(0xFFC6CBB2),
    tertiary = Color(0xFFD4BCE8),
    secondaryContainer = Color(0xFF393D30),
    onSecondaryContainer = Color(0xFFE7E8DA),
    background = Color(0xFF1B1B18),
    surface = Color(0xFF252521),
    surfaceVariant = Color(0xFF38362F),
    surfaceContainer = Color(0xFF2B2A25),
    surfaceContainerLow = Color(0xFF22221E),
    surfaceContainerHigh = Color(0xFF34322C),
    outline = Color(0xFFA69B8D),
    outlineVariant = Color(0xFF4C483F),
    onSurfaceVariant = Color(0xFFCCC3B5),
    onSurface = Color(0xFFF0ECE2),
    onBackground = Color(0xFFF0ECE2),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 29.sp, lineHeight = 35.sp, letterSpacing = (-0.7).sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
)
private val AppShapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp))

@Composable
fun AsistenteVialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
