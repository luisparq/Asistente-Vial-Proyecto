package com.tecsup.asistente.conduccion.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B60),
    secondary = Color(0xFF4B635E),
    tertiary = Color(0xFF416277),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF73D7C6),
    secondary = Color(0xFFB2CCC5),
    tertiary = Color(0xFFA8CCE3),
)

@Composable
fun AsistenteVialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
