package com.tecsup.asistente.conduccion.core.ui

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tecsup.asistente.conduccion.R

/** Espejo nativo de backend/public/css/avigem-tokens.css. */
object AvigemColors {
    val Sillar50 = Color(0xFFFFFDF8)
    val Sillar100 = Color(0xFFF4F1E9)
    val Sillar200 = Color(0xFFEAE6DA)
    val Sillar300 = Color(0xFFD9D4C5)
    val Mineral400 = Color(0xFFA9A79B)
    val Mineral600 = Color(0xFF5F6058)
    val Volcanic = Color(0xFF15161A)
    val Graphite = Color(0xFF26282D)
    val Graphite700 = Color(0xFF34373D)
    val Citrine = Color(0xFFD4F23A)
    val Citrine300 = Color(0xFFE0F76E)
}

private val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight.Normal),
    Font(R.font.archivo_medium, FontWeight.Medium),
    Font(R.font.archivo_semibold, FontWeight.SemiBold),
    Font(R.font.archivo_bold, FontWeight.Bold),
)
val ArchivoExpanded = FontFamily(Font(R.font.archivo_expanded_bold, FontWeight.Bold))

private val LightColors = lightColorScheme(
    primary = AvigemColors.Volcanic,
    onPrimary = AvigemColors.Sillar50,
    primaryContainer = AvigemColors.Citrine,
    onPrimaryContainer = AvigemColors.Volcanic,
    secondary = AvigemColors.Mineral600,
    onSecondary = AvigemColors.Sillar50,
    secondaryContainer = AvigemColors.Sillar200,
    onSecondaryContainer = AvigemColors.Volcanic,
    tertiary = Color(0xFF22507A),
    onTertiary = AvigemColors.Sillar50,
    background = AvigemColors.Sillar100,
    onBackground = AvigemColors.Volcanic,
    surface = AvigemColors.Sillar50,
    onSurface = AvigemColors.Volcanic,
    surfaceVariant = AvigemColors.Sillar200,
    onSurfaceVariant = AvigemColors.Mineral600,
    surfaceContainerLowest = AvigemColors.Sillar50,
    surfaceContainerLow = AvigemColors.Sillar50,
    surfaceContainer = AvigemColors.Sillar200,
    surfaceContainerHigh = AvigemColors.Sillar200,
    surfaceContainerHighest = AvigemColors.Sillar300,
    outline = AvigemColors.Mineral400,
    outlineVariant = AvigemColors.Sillar300,
    error = Color(0xFF9C2F1B),
    onError = AvigemColors.Sillar50,
    errorContainer = Color(0xFFF9E1DA),
    onErrorContainer = Color(0xFF6B1F11),
)

private val DarkColors = darkColorScheme(
    primary = AvigemColors.Citrine,
    onPrimary = AvigemColors.Volcanic,
    primaryContainer = AvigemColors.Citrine,
    onPrimaryContainer = AvigemColors.Volcanic,
    secondary = AvigemColors.Mineral400,
    onSecondary = AvigemColors.Volcanic,
    secondaryContainer = AvigemColors.Graphite700,
    onSecondaryContainer = Color(0xFFF1EFE7),
    tertiary = Color(0xFFA5CCEF),
    onTertiary = AvigemColors.Volcanic,
    background = AvigemColors.Volcanic,
    onBackground = Color(0xFFF1EFE7),
    surface = AvigemColors.Graphite,
    onSurface = Color(0xFFF1EFE7),
    surfaceVariant = AvigemColors.Graphite700,
    onSurfaceVariant = Color(0xFFC6C4B9),
    surfaceContainerLowest = AvigemColors.Volcanic,
    surfaceContainerLow = AvigemColors.Graphite,
    surfaceContainer = AvigemColors.Graphite,
    surfaceContainerHigh = AvigemColors.Graphite700,
    surfaceContainerHighest = AvigemColors.Graphite700,
    outline = AvigemColors.Mineral400,
    outlineVariant = AvigemColors.Graphite700,
    error = Color(0xFFF5B4A6),
    onError = Color(0xFF472017),
    errorContainer = Color(0xFF472017),
    onErrorContainer = Color(0xFFF5B4A6),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0, family: FontFamily = Archivo) =
    TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

private val AppTypography = Typography(
    displaySmall = style(36, 40, FontWeight.Bold, -0.5, ArchivoExpanded),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.5, ArchivoExpanded),
    headlineMedium = style(26, 32, FontWeight.Bold, -0.3, ArchivoExpanded),
    headlineSmall = style(22, 28, FontWeight.SemiBold, -0.2),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(17, 24, FontWeight.SemiBold),
    titleSmall = style(15, 21, FontWeight.SemiBold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 21),
    bodySmall = style(13, 19),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(13, 18, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium),
)
private val AppShapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(16.dp))

@Composable
fun AsistenteVialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** True si el usuario desactivó las animaciones del sistema. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
