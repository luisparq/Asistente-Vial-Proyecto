package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.core.ui.PresentationSection
import com.tecsup.asistente.conduccion.domain.processing.*
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import java.util.Locale
import kotlin.math.abs

@Composable
fun ImuLaboratoryContent(lab: LaboratoryUiState, calibration: CalibrationUiState,
    onSynthetic: (DrivingEventType?) -> Unit, onObserve: (ImuAxis, Int, ImuAxis, Boolean) -> Unit,
    onStop: () -> Unit) {
    PresentationSection("Señales en tiempo real · últimos 30 s") {
        Text("Escalas nominales. X azul · Y verde · Z naranja. No acreditan precisión física.")
        if (lab.history.isEmpty()) Text("Sin muestras actuales. Conecta BLE y espera recepción IMU.")
        else {
            SignalChart(lab.history, false, null)
            SignalChart(lab.history, true, if(lab.corrected) calibration.result?.estimatedGyroOffsetsDps else null)
            Text("${lab.history.size} muestras en búfer · tiempo de recepción Android")
        }
    }
    PresentationSection("Laboratorio del detector · TEST_ONLY") {
        Text("Sin eventos oficiales, alertas ni almacenamiento. Umbrales sintéticos: ±0.30 g / 30 °/s; liberación 0.10 g / 10 °/s. Mínimo 3 muestras y 500 ms; intervalo máximo 400 ms; ventana 2 s; cooldown 1.5 s.")
        Text("Ensayos sintéticos: eje longitudinal X+, giro Z, reposo (0,0,1) g. No son muestras físicas.")
        OutlinedButton(onClick = { onSynthetic(null) }) { Text("Ensayar reposo") }
        listOf(DrivingEventType.HARSH_BRAKING to "Ensayar frenada", DrivingEventType.HARSH_ACCELERATION to "Ensayar aceleración",
            DrivingEventType.SHARP_TURN to "Ensayar giro").forEach { (type, title) ->
            OutlinedButton(onClick = { onSynthetic(type) }) { Text(title) }
        }
        var axis by remember { mutableStateOf(ImuAxis.X) }
        var turn by remember { mutableStateOf(ImuAxis.Z) }
        var sign by remember { mutableIntStateOf(1) }
        var offsets by remember { mutableStateOf(false) }
        LaunchedEffect(calibration.result) { if (calibration.result?.isStableForLab() != true) offsets = false }
        Text("Observación BLE opcional: identifica la orientación de montaje y mantén el módulo inmóvil al capturar la referencia. Una inclinación posterior puede producir falsos positivos.")
        OutlinedButton(onClick = { axis = ImuAxis.entries[(axis.ordinal + 1) % 3] }, enabled = !lab.observing) { Text("Eje longitudinal: $axis") }
        OutlinedButton(onClick = { sign = -sign }, enabled = !lab.observing) { Text("Sentido hacia adelante: ${if(sign == 1) "+" else "−"}") }
        OutlinedButton(onClick = { turn = ImuAxis.entries[(turn.ordinal + 1) % 3] }, enabled = !lab.observing) { Text("Eje de giro: $turn") }
        Row {
            Checkbox(checked = offsets, onCheckedChange = { offsets = it }, enabled = !lab.observing && calibration.result?.isStableForLab() == true)
            Text("Comparar gyro con offsets experimentales transitorios")
        }
        if (lab.observing) OutlinedButton(onClick = onStop) { Text("Detener observación BLE") }
        else Button(onClick = { onObserve(axis, sign, turn, offsets) }, enabled = lab.history.isNotEmpty() && (!offsets || calibration.result?.isStableForLab() == true)) { Text("Capturar reposo y observar BLE") }
        Text(lab.message)
        Text("Posibles candidatos: ${lab.candidates}. Ninguno confirmado ni registrado.", style = MaterialTheme.typography.titleSmall)
        lab.candidateDetails.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        lab.evaluations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (lab.corrected) Text("Gráfica gyro: línea continua original; discontinua corregida. Aceleración sin corrección. Offsets descartados al salir o perder BLE.")
    }
}

@Composable
private fun SignalChart(samples: List<PhysicalImuSample>, gyro: Boolean, offsets: Vector3?) {
    fun vector(s: PhysicalImuSample) = if(gyro) s.angularVelocityDps else s.accelerationG
    val values = samples.flatMap { val v = vector(it); listOf(v.x, v.y, v.z) +
        (offsets?.let { o -> val c = v - o; listOf(c.x, c.y, c.z) } ?: emptyList()) }
    val range = maxOf(if(gyro) 1.0 else 0.1, values.maxOfOrNull { abs(it) } ?: 1.0)
    val unit = if(gyro) "°/s" else "g"
    Text("${if(gyro) "Giroscopio" else "Aceleración"} · escala ±${String.format(Locale.ROOT, "%.2f", range)} $unit", style = MaterialTheme.typography.titleSmall)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val colors = listOf(Color(0xFF316BC2), Color(0xFF00856A), Color(0xFFBE6411))
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "Gráfica ${if(gyro) "giroscopio" else "aceleración"}, X Y Z, últimos 30 segundos" }) {
        val end = samples.last().receivedAtMillis
        fun position(s: PhysicalImuSample, axis: Int, corrected: Boolean): Offset {
            val v = vector(s).let { if(corrected && offsets != null) it - offsets else it }
            val value = when(axis) { 0 -> v.x; 1 -> v.y; else -> v.z }
            return Offset(((s.receivedAtMillis - (end - 30_000)).toFloat() / 30_000).coerceIn(0f, 1f) * size.width,
                size.height / 2 - (value / range).toFloat() * size.height / 2)
        }
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
        samples.zipWithNext().forEach { (a, b) ->
            // Never bridge a missing reception interval as a continuous measured signal.
            if (b.receivedAtMillis - a.receivedAtMillis <= 500) repeat(3) { axis ->
                drawLine(colors[axis], position(a, axis, false), position(b, axis, false), 2.dp.toPx())
                if(offsets != null) drawLine(colors[axis], position(a, axis, true), position(b, axis, true),
                    1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
        }
        if(samples.size == 1) repeat(3) { drawCircle(colors[it], 3.dp.toPx(), position(samples.first(), it, false)) }
    }
}
