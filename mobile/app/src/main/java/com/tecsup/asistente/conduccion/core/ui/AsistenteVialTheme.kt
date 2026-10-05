package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
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
    surface = Color(0xFFF6F9FA),
    surfaceVariant = Color(0xFFE7EEEF),
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

@Composable
fun AsistenteVialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
