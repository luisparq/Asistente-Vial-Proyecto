package com.tecsup.asistente.conduccion.feature.diagnostic

import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.domain.processing.*
import java.io.File
import java.io.BufferedWriter
import java.util.UUID

enum class FieldPhase { IDLE, REST, RECORDING }
enum class FieldLabel(val title: String) {
    NORMAL("Marcha normal"), ACCELERATION("Aceleración"), BRAKING("Frenada"),
    TURN_LEFT("Giro izquierdo"), TURN_RIGHT("Giro derecho"), STOPPED("Detenido")
}
data class FieldRecordingState(
    val phase: FieldPhase = FieldPhase.IDLE,
    val samples: Int = 0, val elapsedMillis: Long = 0, val gaps: Int = 0,
    val label: FieldLabel = FieldLabel.NORMAL,
    val reference: ExperimentalCalibrationResult? = null,
    val referenceMounting: String? = null,
    val latest: RawImuSample? = null,
    val files: List<File> = emptyList(),
    val message: String = "Fija el sensor al vehículo y mide el reposo antes de salir."
) {
    val hz: Double get() = if (elapsedMillis > 0) (samples - 1).coerceAtLeast(0) * 1000.0 / elapsedMillis else 0.0
}

/** Owned by one IO coroutine. Flushes every row: partial recordings survive process death.
 * Timestamps are Android reception times, never claimed as sensor acquisition times.
 */
class FieldRecording(private val directory: File) {
    var state = FieldRecordingState()
        private set
    private var writer: BufferedWriter? = null
    private var start = 0L
    private var last: Long? = null
    private var rest = ExperimentalCalibrationAccumulator()
    private var referenceDevice: String? = null
    private var device = ""
    private var mounting = ""

    fun refresh() { directory.mkdirs(); state = state.copy(files = directory.listFiles()
        ?.filter { it.extension == "csv" }?.sortedByDescending { it.lastModified() } ?: emptyList()) }

    fun begin(isRest: Boolean, now: Long, deviceAddress: String, mountingDescription: String) {
        check(state.phase == FieldPhase.IDLE)
        check(isRest || (state.reference?.isStableForLab() == true && referenceDevice == deviceAddress && state.referenceMounting == mountingDescription))
        require(mountingDescription.isNotBlank())
        refresh()
        device = deviceAddress
        mounting = mountingDescription
        val file = File(directory, "${now}_${if (isRest) "reposo" else "recorrido"}_${UUID.randomUUID().toString().take(8)}.csv")
        writer = file.bufferedWriter()
        writer!!.write(HEADER + "\n")
        writer!!.flush()
        start = now; last = null; rest = ExperimentalCalibrationAccumulator()
        state = state.copy(phase = if (isRest) FieldPhase.REST else FieldPhase.RECORDING,
            samples = 0, elapsedMillis = 0, gaps = 0, label = FieldLabel.NORMAL,
            reference = if (isRest) null else state.reference,
            message = if (isRest) "Quieto durante 30 s. Motor apagado; no toques el montaje." else "Grabando en el teléfono. No necesita Internet.")
        refresh()
    }

    fun label(label: FieldLabel) { state = state.copy(label = label) }
    fun message(text: String) { state = state.copy(message = text) }
    fun checkDevice(address: String?) {
        if ((state.phase != FieldPhase.IDLE && address != device) ||
            (state.reference != null && address != referenceDevice)) {
            finish("Dispositivo cambiado; repite el reposo", last ?: start, invalidate = true)
        }
    }

    fun sample(raw: RawImuSample, gps: GpsTelemetry, now: Long, wallMillis: Long) {
        state = state.copy(latest = raw)
        if (state.phase == FieldPhase.IDLE) return
        val gap = last?.let { now - it > 750 } ?: false // expected firmware rate: 4 Hz
        val elapsed = now - start
        val p = ImuConverter().convert(raw)
        val ref = state.reference?.nominal?.map { it.mean }
        val a = p.accelerationG; val g = p.angularVelocityDps
        val gpsAge = gps.ageMillis?.let { age -> gps.receivedAtMonotonicMillis?.let { age + (now - it).coerceAtLeast(0) } }
        val gpsValid = gps.status == GpsStatus.VALID && gpsAge != null && gpsAge <= GpsDecoder.MAX_AGE_MILLIS
        val row = listOf("SAMPLE", wallMillis, now, elapsed, device, mounting, state.phase,
            if (state.phase == FieldPhase.REST) "REST" else state.label.name,
            raw.ax, raw.ay, raw.az, raw.gx, raw.gy, raw.gz,
            a.x, a.y, a.z, g.x, g.y, g.z) +
            (0..5).map { ref?.get(it) } +
            listOf(ref?.let { a.x-it[0] }, ref?.let { a.y-it[1] }, ref?.let { a.z-it[2] },
                ref?.let { g.x-it[3] }, ref?.let { g.y-it[4] }, ref?.let { g.z-it[5] },
                if (gps.status == GpsStatus.VALID && !gpsValid) GpsStatus.STALE else gps.status,
                gps.latitude.takeIf { gpsValid }, gps.longitude.takeIf { gpsValid }, gpsAge, gps.satellites,
                gps.receivedAtMillis, if (gap) 1 else 0, "")
        write(row)
        last = now
        if (state.phase == FieldPhase.REST) rest.add(raw)
        state = state.copy(samples = state.samples + 1, elapsedMillis = elapsed, gaps = state.gaps + if (gap) 1 else 0)
    }

    fun tick(now: Long) {
        if (state.phase == FieldPhase.IDLE) return
        state = state.copy(elapsedMillis = now-start)
        if (state.phase == FieldPhase.REST && now-start >= 30_000) finish("Reposo terminado", now)
        else if (now - (last ?: start) > 2_000) finish("Recepción interrumpida; vuelve a conectar y repite el reposo", now, invalidate = true)
    }

    fun finish(reason: String, now: Long, invalidate: Boolean = false) {
        if (state.phase == FieldPhase.IDLE) {
            if (invalidate) state = state.copy(reference = null, message = reason)
            return
        }
        val wasRest = state.phase == FieldPhase.REST
        val result = if (wasRest && !invalidate) rest.result()?.takeIf { it.isStableForLab() && state.gaps == 0 } else null
        // A full-width marker distinguishes explicit close from a file interrupted by process death.
        write(List(HEADER.split(',').size) { index -> when(index) {
            0 -> "END"; 2 -> now; 3 -> now-start; 4 -> device; 5 -> mounting
            6 -> state.phase; HEADER.split(',').lastIndex -> reason; else -> null
        } })
        writer?.close(); writer = null
        if (wasRest) referenceDevice = device.takeIf { result != null }
        state = state.copy(phase = FieldPhase.IDLE,
            reference = if (invalidate) null else if (wasRest) result else state.reference,
            referenceMounting = if (invalidate) null else if (wasRest) mounting.takeIf { result != null } else state.referenceMounting,
            message = when {
                invalidate -> "$reason. Archivo parcial conservado."
                wasRest && result == null -> "Reposo insuficiente o inestable. Repite 30 s sin mover el sensor. CSV conservado."
                wasRest -> "Referencia lista. Ya puedes grabar el recorrido."
                else -> "$reason. CSV guardado en el teléfono."
            })
        refresh()
    }

    fun abandon() {
        runCatching { writer?.close() }; writer = null
        state = state.copy(phase = FieldPhase.IDLE, reference = null, referenceMounting = null)
    }
    private fun write(row: List<Any?>) {
        writer!!.write(row.joinToString(",") { value ->
            val text = value?.toString() ?: ""
            "\"" + text.replace("\"", "\"\"") + "\""
        } + "\n")
        writer!!.flush()
    }

    companion object {
        const val HEADER = "row_type,android_utc_ms,android_monotonic_ms,elapsed_ms,device,mounting,phase,label,ax_raw,ay_raw,az_raw,gx_raw,gy_raw,gz_raw,ax_g,ay_g,az_g,gx_dps,gy_dps,gz_dps,rest_ax_g,rest_ay_g,rest_az_g,rest_gx_dps,rest_gy_dps,rest_gz_dps,delta_ax_g,delta_ay_g,delta_az_g,corrected_gx_dps,corrected_gy_dps,corrected_gz_dps,gps_status,latitude,longitude,gps_age_ms,satellites,gps_android_utc_ms,gap_before,stop_reason"
    }
}
