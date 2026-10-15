package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import com.tecsup.asistente.conduccion.domain.diagnostic.RawImuSample
import com.tecsup.asistente.conduccion.domain.diagnostic.ImuRawDecoder
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus
import com.tecsup.asistente.conduccion.domain.processing.ImuPipeline
import com.tecsup.asistente.conduccion.domain.processing.ImuProcessingResult
import com.tecsup.asistente.conduccion.domain.processing.ExperimentalCalibrationAccumulator
import com.tecsup.asistente.conduccion.domain.processing.ExperimentalCalibrationResult
import com.tecsup.asistente.conduccion.domain.processing.*
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin

data class CalibrationUiState(val measuring: Boolean = false, val remainingSeconds: Int = 30,
    val validSamples: Int = 0, val result: ExperimentalCalibrationResult? = null, val message: String? = null)

data class DiagnosticUiState(
    val connection: BleConnectionState = BleConnectionState(),
    val sensorStatus: SensorDataStatus = SensorDataStatus.DISCONNECTED,
    val sample: RawImuSample? = null,
    val processing: ImuProcessingResult? = null,
    val gps: com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry = com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry(),
)

data class LaboratoryUiState(val history: List<PhysicalImuSample> = emptyList(),
    val observing: Boolean = false, val corrected: Boolean = false,
    val message: String = "Detector BLE deshabilitado. Ejecuta primero patrones sintéticos TEST_ONLY.",
    val evaluations: List<String> = emptyList(), val candidates: Int = 0, val candidateDetails: List<String> = emptyList())

@HiltViewModel
class DiagnosticViewModel @Inject constructor(private val repository: BleRepository, private val source: SensorDataSource) : ViewModel() {
    private val mutableLab = MutableStateFlow(LaboratoryUiState())
    val laboratory = mutableLab.asStateFlow()
    private val chartBuffer = ImuChartBuffer()
    private var chartJob: Job? = null
    private var chartAddress: String? = null
    private var labConfiguration: DetectorConfiguration? = null
    private var labDetector = DrivingEventDetector()
    private var labProcessor = ImuProcessor()

    fun startCharts() {
        if (chartJob?.isActive == true) return
        chartJob = viewModelScope.launch {
            source.frames.collect { bytes ->
                if (bytes.size != ImuRawDecoder.FRAME_SIZE || repository.connection.value.status != ConnectionStatus.CONNECTED ||
                    source.status.value != SensorDataStatus.RECEIVING) return@collect
                val address = repository.connection.value.device?.address
                if (chartAddress != address) {
                    chartBuffer.clear(); stopObservation(); chartAddress = address
                }
                // Monotonic clock for laboratory windows; does not change persisted event timestamps.
                val sample = ImuConverter().convert(ImuRawDecoder.decode(bytes, android.os.SystemClock.elapsedRealtime()))
                mutableLab.value = laboratory.value.copy(history = chartBuffer.add(sample))
                if (laboratory.value.observing) evaluate(sample)
            }
        }
    }
    fun stopCharts() {
        chartJob?.cancel(); chartJob = null; chartBuffer.clear(); chartAddress = null
        labConfiguration = null; labDetector.reset(); labProcessor = ImuProcessor()
        mutableLab.value = LaboratoryUiState()
    }
    fun stopObservation() {
        labConfiguration = null; labDetector.reset(); labProcessor = ImuProcessor()
        mutableLab.value = laboratory.value.copy(observing = false, corrected = false, evaluations = emptyList(), candidates = 0, candidateDetails = emptyList(),
            message = "Observación detenida. No se guardaron eventos.")
    }
    fun observeBle(axis: ImuAxis, sign: Int, turn: ImuAxis, useGyroOffsets: Boolean) {
        val current = laboratory.value.history.lastOrNull() ?: return
        if (repository.connection.value.status != ConnectionStatus.CONNECTED || source.status.value != SensorDataStatus.RECEIVING) return
        val result = calibration.value.result
        if (useGyroOffsets && result?.isStableForLab() != true) return
        labProcessor = ImuProcessor(if (useGyroOffsets) ImuCalibration(Vector3(0.0, 0.0, 0.0), result!!.estimatedGyroOffsetsDps) else null)
        labConfiguration = TestOnlyLaboratory.configuration(axis, sign, turn, current.accelerationG)
        labDetector = DrivingEventDetector(labConfiguration)
        mutableLab.value = laboratory.value.copy(observing = true, corrected = useGyroOffsets, candidates = 0, candidateDetails = emptyList(), evaluations = emptyList(),
            message = "BLE experimental TEST_ONLY: referencia de reposo capturada por petición explícita. No son eventos confirmados.")
    }
    private fun evaluate(sample: PhysicalImuSample) {
        val config = labConfiguration ?: return
        val processed = labProcessor.process(sample)
        val candidates = labDetector.detect(processed)
        val types = listOf(DrivingEventType.HARSH_ACCELERATION, DrivingEventType.HARSH_BRAKING, DrivingEventType.SHARP_TURN)
        val criteria = listOf(config.acceleration, config.braking, config.turn)
        val signals = TestOnlyLaboratory.signals(processed, config)
        mutableLab.value = laboratory.value.copy(candidates = laboratory.value.candidates + candidates.size,
            candidateDetails = (laboratory.value.candidateDetails + candidates.map {
                "${it.type}: pico %.3f, ${it.sampleCount} muestras, ${it.occurredAtMillis - it.startedAtMillis} ms · candidato TEST_ONLY".format(java.util.Locale.ROOT, it.peak)
            }).takeLast(10),
            evaluations = types.mapIndexed { index, type ->
                val c = criteria[index]
                "$type: %.3f / entrada %.2f / salida %.2f · %s\n%s".format(java.util.Locale.ROOT,
                    signals[index], c.trigger, c.release, if (signals[index] >= c.trigger) "umbral cumplido" else "bajo umbral",
                    labDetector.evidence(type, processed.receivedAtMillis))
            })
    }
    fun runSynthetic(type: DrivingEventType?) {
        stopObservation()
        labConfiguration = TestOnlyLaboratory.configuration(); labDetector = DrivingEventDetector(labConfiguration); labProcessor = ImuProcessor()
        TestOnlyLaboratory.synthetic(type).forEach(::evaluate)
        mutableLab.value = laboratory.value.copy(message = "Ensayo sintético reproducible TEST_ONLY: ${type ?: "REPOSO"}. No usa ni sustituye datos BLE.")
        labConfiguration = null
    }

    private val mutableCalibration = MutableStateFlow(CalibrationUiState())
    val calibration = mutableCalibration.asStateFlow()
    private var measurementJob: Job? = null
    private var measurementAddress: String? = null

    init {
        viewModelScope.launch {
            combine(repository.connection, source.status) { connection, status -> connection to status }
                .collect { (connection, status) ->
                    if (connection.status != ConnectionStatus.CONNECTED || status != SensorDataStatus.RECEIVING) {
                        chartBuffer.clear(); stopObservation()
                        mutableLab.value = laboratory.value.copy(history = emptyList())
                    }
                    val active = calibration.value
                    if ((active.measuring || active.result != null) &&
                        (connection.status != ConnectionStatus.CONNECTED || connection.device?.address != measurementAddress)) {
                        discardMeasurement("Conexión BLE perdida o cambiada. Medición y resultados descartados.")
                    } else if (active.measuring && status != SensorDataStatus.RECEIVING) {
                        discardMeasurement("Recepción de muestras interrumpida. Medición descartada; vuelve a intentarlo.")
                    }
                }
        }
    }

    fun startMeasurement() {
        if (calibration.value.measuring) return
        if (repository.connection.value.status != ConnectionStatus.CONNECTED ||
            source.status.value != SensorDataStatus.RECEIVING || source.sample.value == null) {
            mutableCalibration.value = CalibrationUiState(message = "Conecta el dispositivo y espera muestras reales antes de iniciar.")
            return
        }
        if (laboratory.value.corrected) stopObservation()
        measurementAddress = repository.connection.value.device?.address
        mutableCalibration.value = CalibrationUiState(measuring = true)
        measurementJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineScope {
                val accumulator = ExperimentalCalibrationAccumulator()
                // frames has no replay in the existing BLE source: never count the previous displayed sample.
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    source.frames.collect { bytes ->
                        if (repository.connection.value.status != ConnectionStatus.CONNECTED ||
                            repository.connection.value.device?.address != measurementAddress ||
                            source.status.value != SensorDataStatus.RECEIVING) return@collect
                        if (bytes.size == ImuRawDecoder.FRAME_SIZE && accumulator.add(ImuRawDecoder.decode(bytes))) {
                            mutableCalibration.value = calibration.value.copy(validSamples = accumulator.validSamples)
                        }
                    }
                }
                val countdown = launch {
                    for (remaining in 29 downTo 1) {
                        delay(1000)
                        mutableCalibration.value = calibration.value.copy(remainingSeconds = remaining)
                    }
                }
                delay(30_000) // One fixed window; countdown updates do not extend the measurement.
                collector.cancelAndJoin()
                countdown.cancelAndJoin()
                if (repository.connection.value.status != ConnectionStatus.CONNECTED ||
                    repository.connection.value.device?.address != measurementAddress ||
                    source.status.value != SensorDataStatus.RECEIVING) {
                    mutableCalibration.value = CalibrationUiState(message = "Recepción interrumpida. Resultados descartados.")
                } else {
                    val result = accumulator.result()
                    mutableCalibration.value = CalibrationUiState(remainingSeconds = 0, validSamples = accumulator.validSamples,
                        result = result, message = if (result == null) "No se recibieron muestras válidas. No se estimaron offsets." else null)
                }
            }
        }
    }

    private fun discardMeasurement(message: String) {
        measurementJob?.cancel(); measurementJob = null
        if (laboratory.value.corrected) stopObservation()
        mutableCalibration.value = CalibrationUiState(message = message)
    }
    fun cancelMeasurement() = discardMeasurement("Medición y resultados descartados.")
    fun cancelIfMeasuring() { if (calibration.value.measuring) cancelMeasurement() }

    private val pipeline = ImuPipeline() // No calibration/filter/criteria supplied; detector disabled.
    private var lastResult: ImuProcessingResult? = null
    val state = combine(repository.connection, source.status, source.sample, source.gps) { connection, status, sample, gps ->
        if (connection.status != ConnectionStatus.CONNECTED || status != SensorDataStatus.RECEIVING || sample == null) {
            pipeline.reset(); lastResult = null
            DiagnosticUiState(connection, status, gps = gps)
        } else {
            val result = lastResult?.takeIf { it.raw == sample } ?: pipeline.accept(sample).also { lastResult = it }
            DiagnosticUiState(connection, status, sample, result, gps)
        }
    }.onStart { pipeline.reset(); lastResult = null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), DiagnosticUiState())
}
