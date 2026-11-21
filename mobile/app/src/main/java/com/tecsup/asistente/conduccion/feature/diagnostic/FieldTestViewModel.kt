package com.tecsup.asistente.conduccion.feature.diagnostic

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import com.tecsup.asistente.conduccion.domain.processing.*
import com.tecsup.asistente.conduccion.data.local.VehicleProfileStore

data class VehicleLabState(
    val history: List<PhysicalImuSample> = emptyList(),
    val learning: NormalManeuver? = null, val learningSamples: Int = 0,
    val learned: Set<NormalManeuver> = emptySet(), val testing: Boolean = false,
    val profile: LearnedVehicleProfile? = null, val savedProfile: LearnedVehicleProfile? = null,
    val message: String = "Conecta el sensor y mide el reposo.",
    val detections: List<VehicleExcess> = emptyList(),
    val buzzer: String = "Sin órdenes", val reading: VehicleReading? = null,
    val learningSeconds: Int = 0
)

@HiltViewModel
class FieldTestViewModel @Inject constructor(
    private val ble: BleRepository, private val source: SensorDataSource,
    @ApplicationContext private val context: Context,
    private val profiles: VehicleProfileStore,
) : ViewModel() {
    private val recorder = FieldRecording(File(context.filesDir, "field-tests"))
    private val mutable = MutableStateFlow(FieldRecordingState())
    val recording = mutable.asStateFlow()
    private val learner = VehicleLearning()
    private val charts = ImuChartBuffer(capacity = 1200)
    private val mutableLab = MutableStateFlow(VehicleLabState())
    val lab = mutableLab.asStateFlow()
    private var savedProfile: LearnedVehicleProfile? = null
    private var deviceAddress: String? = null
    private var learningStarted = 0L
    private var buzzerDeadline: Long? = null
    private var sessionVerified = false
    val connection = ble.connection
    val sensorStatus = source.status
    val gps = source.gps
    private val operations = Channel<() -> Unit>(256)
    @Volatile private var visible = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            recorder.refresh(); mutable.value = recorder.state
            try {
                for (operation in operations) {
                    try {
                        operation(); mutable.value = recorder.state
                        mutableLab.value = mutableLab.value.copy(learning = learner.learning,
                            learningSamples = learner.sampleCount, learned = learner.learned,
                            profile = learner.profile, savedProfile = savedProfile, message = learner.message)
                    }
                    catch (e: Exception) {
                        recorder.abandon()
                        recorder.message("No se pudo guardar: ${e.message}. Revisa el almacenamiento antes de repetir.")
                        mutable.value = recorder.state
                        learner.reset(); sessionVerified=false; buzzerDeadline=null
                        mutableLab.value=VehicleLabState(message=recorder.state.message)
                    }
                }
            } finally { recorder.abandon() }
        }
        viewModelScope.launch {
            source.frames.collect { bytes ->
                if (visible && bytes.size == ImuRawDecoder.FRAME_SIZE && ready()) {
                    val now = (System.nanoTime() / 1_000_000)
                    val utc = System.currentTimeMillis()
                    val raw = ImuRawDecoder.decode(bytes, utc)
                    val location = source.gps.value
                    operations.send {
                        val physical=ImuConverter().convert(raw.copy(receivedAtMillis=now))
                        mutableLab.value=mutableLab.value.copy(history=charts.add(physical),reading=learner.reading(physical))
                        learner.acceptLearning(physical)
                        if (mutableLab.value.testing && visible && ready()) {
                            val detection=if (buzzerDeadline==null) learner.detect(physical) else null
                            recorder.label(when(detection?.kind) {
                                NormalManeuver.ACCELERATION -> FieldLabel.ACCELERATION
                                NormalManeuver.BRAKING -> FieldLabel.BRAKING
                                NormalManeuver.TURN -> if ((learner.reading(physical)?.yawDps ?: 0.0)>0) FieldLabel.TURN_LEFT else FieldLabel.TURN_RIGHT
                                null -> FieldLabel.NORMAL
                            })
                            if (detection!=null) {
                                val sent=ble.triggerBuzzer()
                                buzzerDeadline=if(sent) now+2000 else null
                                mutableLab.value=mutableLab.value.copy(detections=(mutableLab.value.detections+detection).takeLast(20),
                                    buzzer=if(sent) "Orden enviada; esperando confirmación ESP32" else "No se pudo enviar la orden al buzzer")
                            }
                        }
                        recorder.sample(raw, location, now, utc)
                    }
                }
            }
        }
        viewModelScope.launch {
            combine(ble.connection, source.status) { c, s -> c to s }.collect { (c,s) ->
                operations.send {
                    recorder.checkDevice(c.device?.address)
                    if(deviceAddress!=c.device?.address) {
                        learner.reset(); sessionVerified=false; charts.clear(); savedProfile=null
                        deviceAddress=c.device?.address
                        savedProfile = deviceAddress?.let { address ->
                            runCatching { profiles.load(address) }.getOrNull()
                        }
                        mutableLab.value=VehicleLabState(savedProfile=savedProfile)
                    }
                }
                if (c.status != ConnectionStatus.CONNECTED || s != SensorDataStatus.RECEIVING) {
                    operations.send {
                        recorder.finish("Conexión o recepción perdida", (System.nanoTime() / 1_000_000), invalidate = true)
                        learner.reset(); sessionVerified=false; charts.clear(); buzzerDeadline=null
                        mutableLab.value=VehicleLabState(savedProfile=savedProfile)
                    }
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                val now = (System.nanoTime() / 1_000_000)
                operations.send {
                    val wasRest=recorder.state.phase==FieldPhase.REST
                    recorder.tick(now)
                    if(wasRest && recorder.state.phase==FieldPhase.IDLE) {
                        recorder.state.reference?.let { learner.setReference(it,savedProfile); sessionVerified=true; persistProfile() }
                    }
                    if(learner.learning!=null) {
                        mutableLab.value=mutableLab.value.copy(learningSeconds=((now-learningStarted)/1000).toInt())
                        if(now-learningStarted>=60_000) finishLearningInternal()
                        else if(recorder.state.phase!=FieldPhase.RECORDING) learner.cancelLearning()
                    }
                    if(mutableLab.value.testing && recorder.state.phase!=FieldPhase.RECORDING) {
                        mutableLab.value=mutableLab.value.copy(testing=false); learner.resetDetection(); buzzerDeadline=null
                    }
                    if(buzzerDeadline?.let { now>=it }==true) {
                        buzzerDeadline=null
                        mutableLab.value=mutableLab.value.copy(buzzer="ESP32 no confirmó BUZZER_DONE; comprueba el buzzer")
                    }
                }
            }
        }
        viewModelScope.launch { ble.buzzerDone.collect { command {
            if(buzzerDeadline!=null) {
                buzzerDeadline=null; mutableLab.value=mutableLab.value.copy(buzzer="ESP32 confirmó el buzzer (BUZZER_DONE)")
            }
        } } }
    }

    private fun ready() = ble.connection.value.status == ConnectionStatus.CONNECTED && source.status.value == SensorDataStatus.RECEIVING
    private fun command(block: () -> Unit) { viewModelScope.launch { operations.send(block) } }
    fun foreground() { visible = true }
    fun background() {
        visible = false
        command { recorder.finish("Pantalla cerrada o aplicación en segundo plano", (System.nanoTime() / 1_000_000),
            invalidate = recorder.state.phase == FieldPhase.REST)
            if(learner.learning!=null) learner.cancelLearning()
            learner.resetDetection(); buzzerDeadline=null
            mutableLab.value=mutableLab.value.copy(testing=false)
        }
    }
    fun stop() = command { recorder.finish("Detenida por el usuario", (System.nanoTime() / 1_000_000),
        invalidate = recorder.state.phase == FieldPhase.REST) }
    private val fixedMounting="Montaje fijo; orientación longitudinal aprendida por aceleración recta"
    private fun persistProfile() {
        val profile = learner.profile ?: return
        profiles.save(deviceAddress ?: return, profile)
        savedProfile = profile
    }
    fun measureRest() = command {
        if(!visible || !ready() || recorder.state.phase!=FieldPhase.IDLE) return@command
        learner.reset(); sessionVerified=false; mutableLab.value=mutableLab.value.copy(testing=false,detections=emptyList())
        recorder.begin(true,System.nanoTime()/1_000_000,deviceAddress ?: return@command,fixedMounting)
    }
    fun learn(kind: NormalManeuver) = command {
        if(!visible || !ready() || !sessionVerified || recorder.state.phase!=FieldPhase.IDLE || mutableLab.value.testing) return@command
        if(learner.begin(kind)) {
            // Keep the last complete profile on disk until the new lessons are complete.
            savedProfile = null
            learningStarted=System.nanoTime()/1_000_000
            recorder.begin(false,learningStarted,deviceAddress!!,fixedMounting)
            recorder.label(when(kind) { NormalManeuver.ACCELERATION -> FieldLabel.ACCELERATION; NormalManeuver.BRAKING -> FieldLabel.BRAKING; NormalManeuver.TURN -> FieldLabel.NORMAL })
        }
    }
    private fun finishLearningInternal() {
        val kind=learner.learning
        learner.finish()
        recorder.finish("Aprendizaje: ${kind?.title}. ${learner.message}",System.nanoTime()/1_000_000)
        learner.profile?.let {
            learner.updateLimits(RoadTestLimits.ACCELERATION_G, RoadTestLimits.BRAKING_G, RoadTestLimits.TURN_DPS)
            persistProfile()
        }
    }
    fun finishLearning() = command { if(learner.learning!=null) finishLearningInternal() }
    fun saveLimits(acceleration: Double, braking: Double, turn: Double) = command {
        if (!visible || !ready() || deviceAddress == null || recorder.state.phase != FieldPhase.IDLE ||
            learner.learning != null || mutableLab.value.testing) return@command
        if (learner.profile == null) learner.restore(savedProfile ?: return@command)
        learner.updateLimits(acceleration, braking, turn)
        persistProfile()
    }

    fun startDetection() = command {
        if(!visible || !ready() || !sessionVerified || learner.profile==null || recorder.state.phase!=FieldPhase.IDLE) return@command
        learner.resetDetection(); buzzerDeadline=null
        recorder.begin(false,System.nanoTime()/1_000_000,deviceAddress ?: return@command,fixedMounting)
        mutableLab.value=mutableLab.value.copy(testing=true,detections=emptyList(),buzzer="Detección activada; sin órdenes")
    }
    fun stopDetection() = command {
        recorder.finish("Prueba de límites aprendidos",System.nanoTime()/1_000_000)
        mutableLab.value=mutableLab.value.copy(testing=false); learner.resetDetection(); buzzerDeadline=null
    }
    fun checkBuzzer() = command {
        if(!visible || !ready() || recorder.state.phase!=FieldPhase.IDLE || buzzerDeadline!=null) return@command
        val sent=ble.triggerBuzzer()
        buzzerDeadline=if(sent) System.nanoTime()/1_000_000+2000 else null
        mutableLab.value=mutableLab.value.copy(buzzer=if(sent) "Prueba de buzzer enviada; esperando ESP32" else "No se pudo enviar la prueba del buzzer")
    }
    fun export(file: File, destination: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                require(file in mutable.value.files && mutable.value.phase == FieldPhase.IDLE)
                context.contentResolver.openOutputStream(destination, "wt")!!.use { output -> file.inputStream().use { it.copyTo(output) } }
            }.onSuccess { command { recorder.message("CSV exportado: ${file.name}") } }
                .onFailure { error -> command { recorder.message("No se pudo exportar el CSV: ${error.message}") } }
        }
    }
}
