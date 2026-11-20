package com.tecsup.asistente.conduccion.feature.diagnostic

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.BuildConfig
import com.tecsup.asistente.conduccion.core.ui.*
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import java.io.File
import java.util.Locale
import com.tecsup.asistente.conduccion.domain.processing.*

@Composable
fun VehicleLabScreen(onBack: () -> Unit, viewModel: FieldTestViewModel = hiltViewModel()) {
    if (!BuildConfig.DEBUG) { ScreenLayout("AVIGEM One", "Herramienta disponible en la versión de pruebas.", onBack); return }
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val lab by viewModel.lab.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val sensor by viewModel.sensorStatus.collectAsStateWithLifecycle()
    val gps by viewModel.gps.collectAsStateWithLifecycle()
    val window = (LocalContext.current as? Activity)?.window
    DisposableEffect(window) {
        val alreadyKept = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { viewModel.background(); if (!alreadyKept) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.foreground() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.background() }
    var exportFile by remember { mutableStateOf<File?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportFile?.let { viewModel.export(it, uri) }
    }
    VehicleLabContent(recording, lab, connection.status == ConnectionStatus.CONNECTED && sensor == SensorDataStatus.RECEIVING,
        gps, onBack, viewModel::measureRest, viewModel::stop, viewModel::learn, viewModel::finishLearning,
        viewModel::startDetection, viewModel::stopDetection, { exportFile = it; exporter.launch(it.name) }, viewModel::checkBuzzer)
}

@Composable
fun VehicleLabContent(state: FieldRecordingState, lab: VehicleLabState, receiving: Boolean, gps: GpsTelemetry,
    onBack: () -> Unit, onRest: () -> Unit, onCancelRest: () -> Unit,
    onLearn: (NormalManeuver) -> Unit, onFinishLearning: () -> Unit,
    onStartDetection: () -> Unit, onStopDetection: () -> Unit, onExport: (File) -> Unit,
    onCheckBuzzer: () -> Unit = {}) {
    var details by rememberSaveable { mutableStateOf(false) }
    var allFiles by rememberSaveable { mutableStateOf(false) }
    val idle = state.phase == FieldPhase.IDLE && lab.learning == null && !lab.testing
    val restReady = state.reference != null
    fun number(v: Double) = String.format(Locale.ROOT, "%.3f", v)
    ScreenLayout("Calibración y prueba", "Conecta → calibra → enseña maniobras normales → prueba el buzzer", onBack) {
        Text(if (receiving) "Sensor conectado · recibiendo lecturas" else "Conecta AVIGEM One desde Dispositivo.")
        Text(if (gps.status == GpsStatus.VALID) "GPS con ubicación" else "GPS sin ubicación · la calibración del MPU sigue disponible", style = MaterialTheme.typography.bodySmall)
        PresentationSection("Gráficas en vivo") {
            if (lab.history.isEmpty()) Text("Esperando lecturas del acelerómetro y giroscopio…")
            else {
                Text("X azul · Y verde · Z naranja")
                SignalChart(lab.history, false, null)
                SignalChart(lab.history, true, state.reference?.estimatedGyroOffsetsDps)
                if (state.reference != null) Text("Giroscopio: la línea discontinua muestra la corrección del reposo.", style = MaterialTheme.typography.bodySmall)
                val duration = lab.history.last().receivedAtMillis - lab.history.first().receivedAtMillis
                val hz = if (duration > 0) (lab.history.size - 1) * 1000.0 / duration else 0.0
                Text(String.format(Locale.ROOT, "Recepción: %.1f muestras/s", hz), style = MaterialTheme.typography.bodySmall)
            }
        }
        PresentationSection("1. Calibrar en reposo") {
            Text("Fija el sensor al carro. Con el carro detenido y el motor apagado, pulsa el botón y déjalo quieto 30 segundos. No tienes que escribir nada.")
            if (state.phase == FieldPhase.REST) {
                Text("${(30 - state.elapsedMillis/1000).coerceAtLeast(0)} s restantes · ${state.samples} muestras")
                OutlinedButton(onClick = onCancelRest) { Text("Cancelar calibración") }
            } else {
                Text(if (restReady) "Reposo calibrado" else if (lab.hasSavedProfile) "Perfil guardado: verifica el reposo antes de usarlo" else "Pendiente de calibrar")
                Button(onClick = onRest, enabled = receiving && idle) { Text(if (restReady) "Recalibrar reposo" else "Calibrar reposo") }
            }
            Text(if (state.phase == FieldPhase.REST || !restReady) state.message else lab.message)
            Text("Si cambias de carro, lugar u orientación del sensor, vuelve a calibrar y enseñar las maniobras.", style = MaterialTheme.typography.bodySmall)
        }
        PresentationSection("2. Aprender lo normal") {
            Text("El acompañante inicia cada medición cuando sea posible hacerla con normalidad. Repite varias veces; no hagas maniobras bruscas para calibrar.")
            if (lab.learning != null) {
                Text("Midiendo: ${lab.learning.title} · ${lab.learningSeconds} s · ${lab.learningSamples} muestras")
                Text(when (lab.learning) {
                    NormalManeuver.ACCELERATION -> "Acelera suavemente en línea recta. En esta medición evita frenar o girar: así se aprende el sentido de avance."
                    NormalManeuver.BRAKING -> "Reduce la velocidad y frena suavemente en línea recta."
                    NormalManeuver.TURN -> "Realiza giros normales en ambos sentidos, cuando el recorrido lo permita."
                })
                Button(onClick = onFinishLearning) { Text("Terminar esta medición") }
                Text("Se detiene como máximo a los 60 segundos. Si faltan muestras, te pedirá repetir.", style = MaterialTheme.typography.bodySmall)
            } else {
                NormalManeuver.entries.forEach { kind ->
                    OutlinedButton(onClick = { onLearn(kind) }, modifier = Modifier.fillMaxWidth(),
                        enabled = receiving && idle && restReady && (kind == NormalManeuver.ACCELERATION || NormalManeuver.ACCELERATION in lab.learned)) {
                        Text((if (kind in lab.learned) "Repetir: " else "Aprender: ") + kind.title.lowercase())
                    }
                }
                Text(lab.message)
            }
        }
        PresentationSection("3. Detectar excesos y probar buzzer") {
            val profile = lab.profile
            if (profile == null) Text("Completa reposo, aceleración, frenada y giros normales para calcular los límites.")
            else {
                Text("Límites calculados para este montaje:")
                Text("Aceleración: ${number(profile.accelerationLimitG)} g · Frenada: ${number(profile.brakingLimitG)} g · Giro: ${number(profile.turnLimitDps)} °/s")
                lab.reading?.let { Text("Ahora: avance ${number(it.longitudinalG)} g · giro ${number(it.yawDps)} °/s") }
                Text("Al superar un límite de forma sostenida, el celular lo detecta y ordena sonar al ESP32. El perfil queda guardado en este teléfono para ese dispositivo.")
            }
            if (lab.testing) {
                Text("Detección activa · ${state.samples} muestras guardadas · ${lab.detections.size} detecciones recientes")
                Button(onClick = onStopDetection, modifier = Modifier.fillMaxWidth()) { Text("Detener prueba y guardar") }
            } else Button(onClick = onStartDetection, enabled = receiving && idle && restReady && profile != null) { Text("Iniciar prueba con buzzer") }
            Text(lab.buzzer)
            OutlinedButton(onClick = onCheckBuzzer, enabled = receiving && idle) { Text("Comprobar buzzer") }
            lab.detections.takeLast(5).reversed().forEach { event ->
                val unit = if (event.kind == NormalManeuver.TURN) "°/s" else "g"
                Text("${when(event.kind) { NormalManeuver.ACCELERATION -> "Aceleración"; NormalManeuver.BRAKING -> "Frenada"; NormalManeuver.TURN -> "Giro" }} sobre límite: ${number(event.value)} $unit (límite ${number(event.limit)})")
            }
            Text("Mantén esta pantalla abierta. Una desconexión detiene la prueba. Son límites experimentales de lo aprendido; no certifican que una maniobra sea peligrosa.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { details = !details }) { Text(if (details) "Ocultar datos y archivos" else "Ver datos y archivos guardados") }
        if (details) PresentationSection("Datos y resultados") {
            state.latest?.let { Text("RAW a: ${it.ax}, ${it.ay}, ${it.az}\nRAW g: ${it.gx}, ${it.gy}, ${it.gz}") }
            Text("Las señales se conservan en CSV durante reposo, aprendizaje y prueba. Guarda una copia para revisarla en Laravel.")
            if (idle) (if (allFiles) state.files else state.files.take(5)).forEach { file ->
                OutlinedButton(onClick = { onExport(file) }) { Text("Exportar ${file.name}") }
            }
            if (state.files.size > 5 && idle) TextButton(onClick = { allFiles = !allFiles }) { Text(if (allFiles) "Ver recientes" else "Ver todos") }
            Text("Margen inicial: 1.8 veces el percentil 95 normal, con mínimos de 0.12 g y 10 °/s y margen de ruido. Requiere 3 muestras y al menos 300 ms; pausa entre avisos de 3 s. Estos criterios son provisionales y requieren validación en el carro.")
            Text("Se aprende el sentido de avance con la aceleración recta y el eje vertical con el reposo. Cambios de inclinación, pendientes o vibraciones pueden alterar la lectura. La resta de reposo no es una compensación completa de gravedad.")
            Text("La gráfica indica la frecuencia de recepción por Bluetooth. En esta prueba el celular calcula la detección y el ESP32 ejecuta la orden del buzzer.")
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class SensorRelayViewModel @javax.inject.Inject constructor(val relay:com.tecsup.asistente.conduccion.data.diagnostics.SensorRelay):androidx.lifecycle.ViewModel()
@Composable
fun DiagnosticScreen(onBack:()->Unit,viewModel:SensorRelayViewModel=hiltViewModel()){
 val state by viewModel.relay.state.collectAsStateWithLifecycle()
 DisposableEffect(viewModel){onDispose{viewModel.relay.stop()}}
 LifecycleEventEffect(Lifecycle.Event.ON_STOP){viewModel.relay.stop()}
 val window=(LocalContext.current as? Activity)?.window
 DisposableEffect(window){window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
 SensorRelayContent(state, { if(state.sending)viewModel.relay.stop() else viewModel.relay.start() }, onBack)
}

@Composable
fun SensorRelayContent(
    state: com.tecsup.asistente.conduccion.data.diagnostics.SensorRelayState,
    onToggleSending: () -> Unit,
    onBack: () -> Unit
) {
    ScreenLayout(
        "Envío de muestras",
        "El ESP32 conserva su calibración y detecta las maniobras. La calibración se administra desde Vialigent.",
        onBack
    ) {
        Text("${state.pending} muestras pendientes · ${state.acknowledged} confirmadas en esta sesión")
        Text(state.message)
        if (state.dropped > 0) Text("${state.dropped} tramas omitidas por saturación del búfer de recepción")
        Button(onClick = onToggleSending, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.sending) "Detener envío de muestras" else "Enviar muestras a administrador")
        }
        Text("Las muestras se reciben por Bluetooth y se reenvían por la API de Laravel. La empresa y Vialigent consultan una sola copia. Mantén esta pantalla abierta para recibir; los pendientes guardados pueden enviarse después.")
    }
}
