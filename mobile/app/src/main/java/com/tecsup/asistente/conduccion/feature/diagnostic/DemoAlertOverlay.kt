package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tecsup.asistente.conduccion.core.ui.AvigemColors
import com.tecsup.asistente.conduccion.core.ui.rememberReducedMotion

private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Solo presentación: el buzzer y la voz siguen controlados por DiagnosticScreen y su ViewModel. */
@Composable
fun DemoAlertOverlay(title: String, observed: String, threshold: String, onDismiss: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val entrance = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val ring = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reducedMotion) return@LaunchedEffect
        entrance.animateTo(1f, tween(220, easing = EaseOut))
        ring.animateTo(1f, tween(520, easing = EaseOut))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(AvigemColors.Volcanic)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)
                    .graphicsLayer {
                        alpha = entrance.value
                        val scale = 0.96f + 0.04f * entrance.value
                        scaleX = scale
                        scaleY = scale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        val growth = ring.value
                        if (growth in 0.001f..0.999f) drawCircle(
                            AvigemColors.Citrine.copy(alpha = 1f - growth),
                            radius = size.minDimension * (0.34f + 0.16f * growth),
                            style = Stroke(2.dp.toPx()),
                        )
                    }
                    Box(Modifier.size(76.dp).background(AvigemColors.Citrine, MaterialTheme.shapes.large), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Warning, null, Modifier.size(40.dp), tint = AvigemColors.Volcanic)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("PRUEBA DEMOSTRATIVA · TEST_ONLY", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = AvigemColors.Mineral400)
                Spacer(Modifier.height(10.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, color = AvigemColors.Sillar100)
                Spacer(Modifier.height(16.dp))
                Text(observed, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, color = AvigemColors.Sillar100)
                Spacer(Modifier.height(4.dp))
                Text("Umbral: $threshold", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, color = AvigemColors.Sillar200)
                Spacer(Modifier.weight(1f))
                Surface(color = AvigemColors.Graphite, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Prueba demostrativa · Umbral TEST_ONLY", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = AvigemColors.Citrine300)
                        Text("No representa una maniobra vehicular validada", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = AvigemColors.Sillar200)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = AvigemColors.Sillar100, contentColor = AvigemColors.Volcanic),
                ) { Text("Cerrar alerta", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
