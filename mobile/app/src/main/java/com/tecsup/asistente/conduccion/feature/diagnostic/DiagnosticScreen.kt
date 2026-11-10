package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.PresentationSection
import com.tecsup.asistente.conduccion.core.ui.StatusBadge
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus
import java.util.Locale
import com.tecsup.asistente.conduccion.domain.processing.isStableForLab
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.core.ui.tone

@Composable
fun DiagnosticScreen(onBack: () -> Unit, viewModel: DiagnosticViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val laboratory by viewModel.laboratory.collectAsStateWithLifecycle()
    val realBleUpload by viewModel.realBleUpload.collectAsStateWithLifecycle()
    val demo by viewModel.demo.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.startCharts() }
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose { viewModel.cancelIfMeasuring(); viewModel.stopCharts() } }

    val context = androidx.compose.ui.platform.LocalContext.current
    var tts by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    androidx.compose.runtime.DisposableEffect(context) {
        var instance: android.speech.tts.TextToSpeech? = null
        instance = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val res = instance?.setLanguage(java.util.Locale("es", "ES"))
                if (res == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || res == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    instance?.language = java.util.Locale.getDefault()
                }
            }
        }
        tts = instance
        onDispose {
            instance?.stop()
            instance?.shutdown()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.cancelIfMeasuring()
        viewModel.stopCharts()
        tts?.stop()
    }
    androidx.compose.runtime.LaunchedEffect(demo.alertVisible) {
        if (demo.alertVisible) {
            tts?.speak(demo.alertMessage, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "demo_tts")
        } else tts?.stop() // Also silences the alert when BLE disconnects or the test is cancelled.
    }

    DiagnosticContent(state, onBack, calibration, viewModel::startMeasurement, viewModel::cancelMeasurement, laboratory, viewModel::runSynthetic, viewModel::observeBle, viewModel::stopObservation,
        realBleUpload, viewModel::startRealBleUpload, viewModel::stopRealBleUpload, demo, viewModel::startDemoTest, viewModel::stopDemoTest, viewModel::dismissDemoAlert)
}

/** Observaciones en memoria de la prueba demostrativa; no son eventos oficiales. */
@Composable
private fun DemoReadouts(demo: DemoTestUiState) {
    val demoConfig = com.tecsup.asistente.conduccion.domain.processing.ManeuverDemoTestOnly
    fun number(format: String, value: Double?) = value?.let { String.format(Locale.ROOT, format, it) } ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(
            "Modo: PRUEBA DEMOSTRATIVA",
            "Umbrales TEST_ONLY: |gz| > ${demoConfig.TURN_THRESHOLD_TEST_ONLY_DPS} °/s · ax corregido > ${number("%+.2f", demoConfig.ACCELERATION_THRESHOLD_TEST_ONLY_G)} g o < ${number("%+.2f", demoConfig.BRAKING_THRESHOLD_TEST_ONLY_G)} g",
            "ax corregido: ${number("%+.3f", demo.axCorrectedG)} g · gz: ${number("%+.1f", demo.gzDps)} °/s",
            "Muestras consecutivas: ${demo.consecutiveSamples}/${demoConfig.CONSECUTIVE_SAMPLES_TEST_ONLY} · Cooldown: ${number("%.1f", demo.cooldownRemainingMillis / 1000.0)} s",
            "Último patrón: ${demo.lastPattern ?: "—"}",
            "Buzzer: ${demo.buzzerCommand}",
            "BUZZER_DONE: ${if (demo.buzzerDoneConfirmed) "confirmado" else "sin confirmar"}",
        ).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DiagnosticContent(state: DiagnosticUiState, onBack: () -> Unit,
    calibration: CalibrationUiState = CalibrationUiState(), onStartMeasurement: () -> Unit = {},
    onCancelMeasurement: () -> Unit = {}, laboratory: LaboratoryUiState = LaboratoryUiState(),
    onSynthetic: (com.tecsup.asistente.conduccion.domain.model.DrivingEventType?) -> Unit = {},
    onObserve: (com.tecsup.asistente.conduccion.domain.processing.ImuAxis, Int, com.tecsup.asistente.conduccion.domain.processing.ImuAxis, Boolean) -> Unit = { _, _, _, _ -> },
    onStopObservation: () -> Unit = {},
    realBleUpload: com.tecsup.asistente.conduccion.data.diagnostics.DiagnosticsCaptureState = com.tecsup.asistente.conduccion.data.diagnostics.DiagnosticsCaptureState(),
    onStartRealBleUpload: () -> Unit = {}, onStopRealBleUpload: () -> Unit = {},
    demo: DemoTestUiState = DemoTestUiState(),
    onStartDemoTest: () -> Unit = {}, onStopDemoTest: () -> Unit = {}, onDismissDemoAlert: () -> Unit = {}) {
    ScreenLayout(stringResource(R.string.diagnostic_title), stringResource(R.string.diagnostic_description), onBack) {
        PresentationSection("Conexión del dispositivo") {
        StatusBadge(stringResource(state.connection.status.labelResource()), state.connection.status.tone())
        state.connection.device?.let {
            Text(it.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.ble_unnamed))
            Text(it.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(stringResource(when (state.sensorStatus) {
            SensorDataStatus.DISCONNECTED -> R.string.connection_disconnected
            SensorDataStatus.WAITING_FOR_DATA -> R.string.diagnostic_waiting
            SensorDataStatus.RECEIVING -> R.string.diagnostic_receiving
            SensorDataStatus.FAILED -> R.string.diagnostic_failed
        }))
        state.connection.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        PresentationSection("GPS NEO-6M") {
        val gps = state.gps
        Text(when (gps.status) {
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.DISCONNECTED -> "GPS desconectado"
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.UNAVAILABLE -> "GPS no disponible en este dispositivo BLE"
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.WAITING -> "Esperando telemetría GPS"
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.NO_RECEPTION -> "GPS sin recepción UART"
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.NO_FIX -> "GPS sin fix válido"
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.VALID -> "Posición GPS válida"
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.STALE -> "Posición GPS caducada"
            com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.INVALID_FRAME -> "Frame GPS inválido"
        })
        if (state.connection.status == ConnectionStatus.CONNECTED && gps.status == com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus.VALID) {
            Text("Latitud: ${gps.latitude} · Longitud: ${gps.longitude}")
            gps.utcSeconds?.let { Text("Hora UTC de la posición: " + java.time.LocalTime.ofSecondOfDay(it.toLong())) }
            gps.ageMillis?.let { Text("Antigüedad al recibir: $it ms") }
        }
        gps.satellites?.let { Text("Satélites usados: $it") }
        }
        PresentationSection("Mediciones del IMU · uso técnico") {
        val sample = state.sample
        if (state.connection.status == ConnectionStatus.CONNECTED && state.sensorStatus == SensorDataStatus.RECEIVING && sample != null) {
            Text("RAW · cuentas int16", style = MaterialTheme.typography.titleMedium)
            listOf(listOf("ax: ${sample.ax}", "ay: ${sample.ay}", "az: ${sample.az}"),
                listOf("gx: ${sample.gx}", "gy: ${sample.gy}", "gz: ${sample.gz}")).forEach { axes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    axes.forEach { reading ->
                        Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                            Text(reading, Modifier.padding(horizontal = 8.dp, vertical = 14.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            state.processing?.takeIf { it.raw == sample }?.let { result ->
                fun axes(x: Double, y: Double, z: Double) = String.format(Locale.ROOT, "X: %.4f · Y: %.4f · Z: %.4f", x, y, z)
                Text("CONVERTIDO · escala nominal", style = MaterialTheme.typography.titleMedium)
                val converted = result.converted
                Text("Accel (g) · " + axes(converted.accelerationG.x, converted.accelerationG.y, converted.accelerationG.z))
                Text("Gyro (°/s) · " + axes(converted.angularVelocityDps.x, converted.angularVelocityDps.y, converted.angularVelocityDps.z))
                val a = converted.accelerationG
                Text(String.format(Locale.ROOT, "Magnitud de aceleración: %.4f g (incluye gravedad)", kotlin.math.sqrt(a.x*a.x + a.y*a.y + a.z*a.z)))
                Text("PROCESADO", style = MaterialTheme.typography.titleMedium)
                val processed = result.processed
                Text("Accel (g) · " + axes(processed.accelerationG.x, processed.accelerationG.y, processed.accelerationG.z))
                Text("Gyro (°/s) · " + axes(processed.angularVelocityDps.x, processed.angularVelocityDps.y, processed.angularVelocityDps.z))
                Text(if (processed.hasCalibrationOffsets) "Offsets configurados; no acreditan validación física." else "Sin calibración física · sin corrección de offsets.")
            }
        } else Text(stringResource(R.string.diagnostic_no_measurements))
        Text("Identidad del sensor no confirmada: WHO_AM_I = 0x75 observado en el módulo de prueba. La conversión es nominal, no una medición físicamente validada.")
        Text("Filtro y detector deshabilitados por defecto. No se crean eventos SENSOR. La aceleración incluye gravedad; no se ha definido la orientación respecto al vehículo.")
        }
        PresentationSection("Prueba demostrativa provisional") {
            if (demo.active) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("PRUEBA DEMOSTRATIVA", style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Umbral TEST_ONLY", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("No representa una maniobra vehicular validada", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text("Duración restante: ${demo.remainingSeconds} s · Detecciones: ${demo.candidates}",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text(demo.statusText, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onStopDemoTest) {
                    Text("Detener prueba demostrativa")
                }
            } else {
                Text("Flujo demostrativo: Movimiento del circuito → Patrón TEST_ONLY en Android → Alerta pantalla completa + Voz → Orden buzzer por BLE → Buzzer ESP32 máximo 300 ms → Confirmación BUZZER_DONE.")
                Text("Al iniciar, mantén el circuito quieto 2 segundos. Duración máxima: 60 segundos. Cooldown anti-repeticiones: 3 segundos.")
                Text(demo.statusText, style = MaterialTheme.typography.bodySmall)
                Button(onClick = onStartDemoTest, enabled = state.connection.status == ConnectionStatus.CONNECTED && state.sensorStatus == SensorDataStatus.RECEIVING) {
                    Text("Iniciar prueba demostrativa")
                }
            }
            DemoReadouts(demo)
        }
        val visibleLab = if (state.connection.status == ConnectionStatus.CONNECTED && state.sensorStatus == SensorDataStatus.RECEIVING)
            laboratory else laboratory.copy(history = emptyList(), observing = false, corrected = false)
        ImuLaboratoryContent(visibleLab, calibration, onSynthetic, onObserve, onStopObservation, demo.triggerTimestampMillis)
        PresentationSection("Envío seguro a Diagnostics · REAL_BLE") {
        Text("Solo se envían cuentas RAW durante una prueba iniciada explícitamente. Usa HTTPS y la sesión autenticada de AVIGEM; si no hay internet, las muestras quedan en la cola local.")
        Text(realBleUpload.message)
        if (realBleUpload.active) {
            Text("Muestras en la captura actual: ${realBleUpload.queuedSamples}")
            OutlinedButton(onClick = onStopRealBleUpload) { Text("Finalizar y sincronizar prueba") }
        } else {
            Button(onClick = onStartRealBleUpload, enabled = state.connection.status == ConnectionStatus.CONNECTED &&
                state.sensorStatus == SensorDataStatus.RECEIVING && state.sample != null) { Text("Iniciar envío REAL_BLE") }
        }
        Text("No acredita identidad, escala ni calibración física del sensor.", style = MaterialTheme.typography.bodySmall)
        }
        if (demo.alertVisible) DemoAlertOverlay(demo.alertTitle, demo.alertObserved, demo.alertThreshold, onDismissDemoAlert)
        PresentationSection("Calibración experimental") {
        Text("Solo desarrollo y mantenimiento. Mantén la IMU inmóvil durante 30 segundos. La inmovilidad no se comprueba automáticamente.")
        Text("Promedios y offsets estimados experimentales: no se aplican ni se guardan permanentemente. WHO_AM_I = 0x75; identidad no confirmada.")
        if (calibration.measuring) {
            Text("Midiendo… ${calibration.remainingSeconds} s restantes · ${calibration.validSamples} muestras válidas")
            OutlinedButton(onClick = onCancelMeasurement) { Text("Cancelar medición") }
        } else {
            Button(onClick = onStartMeasurement, enabled = state.connection.status == ConnectionStatus.CONNECTED &&
                state.sensorStatus == SensorDataStatus.RECEIVING && state.sample != null) { Text("Iniciar medición") }
        }
        calibration.message?.let { Text(it) }
        calibration.result?.takeIf { state.connection.status == ConnectionStatus.CONNECTED }?.let { result ->
            Text("Resultado experimental · 30 segundos", style = MaterialTheme.typography.titleMedium)
            Text("Muestras válidas: ${result.validSamples}. DE muestral (n−1); requiere al menos dos muestras.")
            fun value(number: Double?) = number?.let { String.format(Locale.ROOT, "%.5f", it) } ?: "No disponible"
            listOf("ax", "ay", "az", "gx", "gy", "gz").forEachIndexed { axis, label ->
                val raw = result.raw[axis]
                val nominal = result.nominal[axis]
                val unit = if (axis < 3) "g" else "°/s"
                Text("$label · media ${value(raw.mean)} · DE ${value(raw.sampleStandardDeviation)} cuentas\n" +
                    "Nominal: media ${value(nominal.mean)} · DE ${value(nominal.sampleStandardDeviation)} $unit")
            }
            Text(if (result.isStableForLab()) "Variabilidad compatible con ensayo en reposo (criterio TEST_ONLY)."
                else "Medición inestable o insuficiente: no usar offsets para comparar.")
            Text("Cribado TEST_ONLY: al menos 100 muestras; DE por eje ≤0.02 g y ≤1 °/s. No es validación física.")
            val offsets = result.estimatedGyroOffsetsDps
            Text("Offsets gyro estimados (°/s): X ${value(offsets.x)} · Y ${value(offsets.y)} · Z ${value(offsets.z)}")
            Text("Estiman el sesgo solo bajo la suposición de inmovilidad. No validan el sensor ni la calibración. No se estiman offsets del acelerómetro: sus medidas incluyen gravedad.")
            OutlinedButton(onClick = onCancelMeasurement) { Text("Descartar resultados") }
        }
        }
    }
}
