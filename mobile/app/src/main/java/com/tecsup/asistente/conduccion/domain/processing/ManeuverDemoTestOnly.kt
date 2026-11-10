package com.tecsup.asistente.conduccion.domain.processing

import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import kotlin.math.abs

/**
 * PRUEBA DEMOSTRATIVA · TEST_ONLY: criterios para mover el módulo con la mano, en escala nominal.
 * No representan maniobras peligrosas reales ni generan eventos SENSOR.
 */
object ManeuverDemoTestOnly {
    /** 5895 cuentas a 131 LSB/(°/s). */
    const val TURN_THRESHOLD_TEST_ONLY_DPS = 45.0
    /** 4096 cuentas a 16384 LSB/g. */
    const val ACCELERATION_THRESHOLD_TEST_ONLY_G = 0.25
    const val BRAKING_THRESHOLD_TEST_ONLY_G = -0.25
    const val CONSECUTIVE_SAMPLES_TEST_ONLY = 2
    const val COOLDOWN_TEST_ONLY_MILLIS = 3_000L
    /** A 4 Hz, 450 ms descarta dos muestras separadas por una muestra perdida. */
    const val MAX_SAMPLE_GAP_TEST_ONLY_MILLIS = 450L
    /** Único punto de inversión: usar -1 si el eje X físico apunta al lado contrario. */
    const val LONGITUDINAL_SIGN_TEST_ONLY = 1
    /** 2 s a 4 Hz; el promedio solo vive durante la sesión. */
    const val REST_SAMPLES_TEST_ONLY = 8
    const val REST_MAX_AX_DEVIATION_TEST_ONLY_G = 0.05
    const val REST_MAX_GZ_TEST_ONLY_DPS = 15.0

    fun correctedAx(ax: Double, restAx: Double) = (ax - restAx) * LONGITUDINAL_SIGN_TEST_ONLY

    fun configuration(restAx: Double) = DetectorConfiguration(
        longitudinalAxis = ImuAxis.X,
        forwardSign = LONGITUDINAL_SIGN_TEST_ONLY,
        turnAxis = ImuAxis.Z,
        acceleration = strictlyAbove(ACCELERATION_THRESHOLD_TEST_ONLY_G),
        braking = strictlyAbove(-BRAKING_THRESHOLD_TEST_ONLY_G),
        turn = strictlyAbove(TURN_THRESHOLD_TEST_ONLY_DPS),
        status = CriteriaStatus.TEST_ONLY,
        restAccelerationG = Vector3(restAx, 0.0, 0.0),
    )

    // release = umbral exige superarlo de forma estricta y hace que cualquier muestra por debajo corte la racha.
    // El cooldown es global entre patrones y lo aplica ManeuverDemoTracker.
    private fun strictlyAbove(threshold: Double) = CandidateCriteria(
        trigger = Math.nextUp(threshold), release = threshold, minimumDurationMillis = 1,
        minimumSamples = CONSECUTIVE_SAMPLES_TEST_ONLY,
        maximumGapMillis = MAX_SAMPLE_GAP_TEST_ONLY_MILLIS, cooldownMillis = 0,
    )
}

/** observed: ax corregido con signo para aceleración/frenado; |gz| para giro. */
data class ManeuverDemoDetection(val type: DrivingEventType, val observed: Double, val atMillis: Long)

data class ManeuverDemoReading(
    val restReady: Boolean,
    val restSamples: Int,
    val axCorrectedG: Double? = null,
    val gzDps: Double,
    val consecutiveSamples: Int = 0,
    val cooldownRemainingMillis: Long = 0,
    val detection: ManeuverDemoDetection? = null,
)

/** Estado en memoria de una sesión demostrativa, sobre DrivingEventDetector. No persiste nada. */
class ManeuverDemoTracker {
    private val restAx = mutableListOf<Double>()
    private var restAverage = 0.0
    private var detector: DrivingEventDetector? = null
    private var lastDetectionMillis: Long? = null

    fun reset() {
        restAx.clear(); restAverage = 0.0; detector = null; lastDetectionMillis = null
    }

    /** blocked: hay una alerta abierta o se espera BUZZER_DONE; el candidato no inicia otra alerta. */
    fun accept(sample: ProcessedImuSample, blocked: Boolean): ManeuverDemoReading {
        val ax = sample.accelerationG.x
        val gz = sample.angularVelocityDps.z
        val active = detector ?: return captureRest(ax, gz)
        val now = sample.receivedAtMillis
        val candidates = active.detect(sample)
        var cooldown = cooldownRemaining(now)
        val detection = candidates.takeIf { !blocked && cooldown == 0L }
            ?.maxByOrNull { it.peak / threshold(it.type) }
            ?.let { ManeuverDemoDetection(it.type, if (it.type == DrivingEventType.HARSH_BRAKING) -it.peak else it.peak, now) }
        if (detection != null) {
            lastDetectionMillis = now
            cooldown = ManeuverDemoTestOnly.COOLDOWN_TEST_ONLY_MILLIS
        }
        return ManeuverDemoReading(
            restReady = true,
            restSamples = ManeuverDemoTestOnly.REST_SAMPLES_TEST_ONLY,
            axCorrectedG = ManeuverDemoTestOnly.correctedAx(ax, restAverage),
            gzDps = gz,
            consecutiveSamples = DrivingEventType.entries.maxOf(active::consecutiveTriggers),
            cooldownRemainingMillis = cooldown,
            detection = detection,
        )
    }

    private fun captureRest(ax: Double, gz: Double): ManeuverDemoReading {
        val moved = abs(gz) > ManeuverDemoTestOnly.REST_MAX_GZ_TEST_ONLY_DPS ||
            (restAx.isNotEmpty() && abs(ax - restAx.average()) > ManeuverDemoTestOnly.REST_MAX_AX_DEVIATION_TEST_ONLY_G)
        if (moved) restAx.clear() else restAx += ax
        if (restAx.size == ManeuverDemoTestOnly.REST_SAMPLES_TEST_ONLY) {
            restAverage = restAx.average()
            detector = DrivingEventDetector(ManeuverDemoTestOnly.configuration(restAverage))
        }
        return ManeuverDemoReading(restReady = detector != null, restSamples = restAx.size, gzDps = gz)
    }

    private fun cooldownRemaining(now: Long): Long {
        val elapsed = lastDetectionMillis?.let { now - it } ?: return 0
        return if (elapsed in 0 until ManeuverDemoTestOnly.COOLDOWN_TEST_ONLY_MILLIS) ManeuverDemoTestOnly.COOLDOWN_TEST_ONLY_MILLIS - elapsed else 0
    }

    private fun threshold(type: DrivingEventType) = when (type) {
        DrivingEventType.HARSH_ACCELERATION -> ManeuverDemoTestOnly.ACCELERATION_THRESHOLD_TEST_ONLY_G
        DrivingEventType.HARSH_BRAKING -> -ManeuverDemoTestOnly.BRAKING_THRESHOLD_TEST_ONLY_G
        DrivingEventType.SHARP_TURN -> ManeuverDemoTestOnly.TURN_THRESHOLD_TEST_ONLY_DPS
    }
}
