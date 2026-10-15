package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun DiagnosticScreen(onBack: () -> Unit, viewModel: DiagnosticViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val laboratory by viewModel.laboratory.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.startCharts() }
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelIfMeasuring(); viewModel.stopCharts() }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelIfMeasuring(); viewModel.stopCharts() } }
    DiagnosticContent(state, onBack, calibration, viewModel::startMeasurement, viewModel::cancelMeasurement, laboratory, viewModel::runSynthetic, viewModel::observeBle, viewModel::stopObservation)
}

@Composable
fun DiagnosticContent(state: DiagnosticUiState, onBack: () -> Unit,
    calibration: CalibrationUiState = CalibrationUiState(), onStartMeasurement: () -> Unit = {},
    onCancelMeasurement: () -> Unit = {}, laboratory: LaboratoryUiState = LaboratoryUiState(),
    onSynthetic: (com.tecsup.asistente.conduccion.domain.model.DrivingEventType?) -> Unit = {},
    onObserve: (com.tecsup.asistente.conduccion.domain.processing.ImuAxis, Int, com.tecsup.asistente.conduccion.domain.processing.ImuAxis, Boolean) -> Unit = { _, _, _, _ -> },
    onStopObservation: () -> Unit = {}) {
    ScreenLayout(stringResource(R.string.diagnostic_title), stringResource(R.string.diagnostic_description), onBack) {
        PresentationSection("Conexión del dispositivo") {
        StatusBadge(stringResource(state.connection.status.labelResource()))
        state.connection.device?.let {
            Text(it.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.ble_unnamed))
            Text(it.address)
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
            Text("ax: ${sample.ax}")
            Text("ay: ${sample.ay}")
            Text("az: ${sample.az}")
            Text("gx: ${sample.gx}")
            Text("gy: ${sample.gy}")
            Text("gz: ${sample.gz}")
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
        val visibleLab = if (state.connection.status == ConnectionStatus.CONNECTED && state.sensorStatus == SensorDataStatus.RECEIVING)
            laboratory else laboratory.copy(history = emptyList(), observing = false, corrected = false)
        ImuLaboratoryContent(visibleLab, calibration, onSynthetic, onObserve, onStopObservation)
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
