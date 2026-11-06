package com.tecsup.asistente.conduccion.data.diagnostics

import com.tecsup.asistente.conduccion.domain.ble.BleDevice
import com.tecsup.asistente.conduccion.domain.diagnostic.ImuRawDecoder
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import com.tecsup.asistente.conduccion.domain.sync.SyncScheduler
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiagnosticsCaptureState(val active: Boolean = false, val queuedSamples: Int = 0,
    val clientSessionId: String? = null, val message: String = "Envío REAL_BLE detenido.")

@Singleton
class DiagnosticsCaptureRepository @Inject constructor(
    private val dao: DiagnosticsUploadDao,
    private val sessions: SessionStore,
    private val scheduler: SyncScheduler,
    private val clock: Clock,
) {
    private val mutableState = MutableStateFlow(DiagnosticsCaptureState())
    val state = mutableState.asStateFlow()
    private var nextSequence = 0L

    suspend fun start(device: BleDevice): Boolean {
        if (mutableState.value.active) return true
        val owner = sessions.state.value?.ownerKey ?: run {
            mutableState.value = DiagnosticsCaptureState(message = "Inicia sesión en AVIGEM antes de enviar a Diagnostics.")
            return false
        }
        dao.closeAbandoned(owner)
        val id = UUID.randomUUID().toString()
        dao.insertSession(DiagnosticsUploadSessionEntity(id, owner, deviceCode(device.address), clock.millis()))
        nextSequence = 0
        mutableState.value = DiagnosticsCaptureState(true, 0, id,
            "Captura REAL_BLE activa. Las muestras se guardan localmente hasta sincronizar.")
        return true
    }

    suspend fun record(frame: ByteArray) {
        val current = mutableState.value
        val id = current.clientSessionId?.takeIf { current.active } ?: return
        val raw = ImuRawDecoder.decode(frame, clock.millis())
        val sequence = nextSequence++
        dao.insertSample(DiagnosticsUploadSampleEntity(id, sequence, raw.receivedAtMillis,
            raw.ax, raw.ay, raw.az, raw.gx, raw.gy, raw.gz))
        val count = current.queuedSamples + 1
        mutableState.value = current.copy(queuedSamples = count)
        if (count % 20 == 0) scheduler.schedule()
    }

    suspend fun stop() {
        val current = mutableState.value
        val id = current.clientSessionId ?: return
        dao.markCompleting(id)
        scheduler.schedule()
        mutableState.value = DiagnosticsCaptureState(message =
            "Captura detenida. ${current.queuedSamples} muestras pendientes o enviadas por sincronización.")
    }

    private fun deviceCode(address: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(address.toByteArray())
        return "AVIGEM-BLE-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
