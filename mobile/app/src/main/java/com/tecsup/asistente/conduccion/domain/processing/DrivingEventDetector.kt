package com.tecsup.asistente.conduccion.domain.processing

import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import kotlin.math.abs

enum class ImuAxis { X, Y, Z }
enum class CriteriaStatus { EXPERIMENTAL_NOT_VALIDATED, TEST_ONLY }

/** No default thresholds/orientation: configuration is required explicitly and is never validated here. */
data class CandidateCriteria(
    val trigger: Double, val release: Double, val minimumDurationMillis: Long,
    val minimumSamples: Int, val maximumGapMillis: Long, val cooldownMillis: Long,
    val maximumWindowMillis: Long? = null,
) {
    init {
        require(trigger.isFinite() && release.isFinite() && trigger > release && release >= 0)
        require(minimumDurationMillis > 0 && minimumSamples >= 2 && maximumGapMillis > 0 && cooldownMillis >= 0)
        require(maximumWindowMillis == null || maximumWindowMillis >= minimumDurationMillis)
    }
}

data class DetectorConfiguration(
    val longitudinalAxis: ImuAxis, val forwardSign: Int, val turnAxis: ImuAxis,
    val acceleration: CandidateCriteria, val braking: CandidateCriteria, val turn: CandidateCriteria,
    val status: CriteriaStatus,
    /** Fixed rest/gravity reference in sensor axes, AFTER optional offsets. Explicit for each synthetic setup. */
    val restAccelerationG: Vector3? = null,
) {
    init { require(forwardSign == 1 || forwardSign == -1) }
}

/** A software candidate, never a validated maneuver, legal violation or persistent DrivingEvent. */
data class CandidateEvent(val type: DrivingEventType, val startedAtMillis: Long, val occurredAtMillis: Long,
    val sampleCount: Int, val peak: Double, val criteriaStatus: CriteriaStatus)

class DrivingEventDetector(private val configuration: DetectorConfiguration? = null) {
    val enabled get() = configuration?.restAccelerationG != null
    private data class Window(var start: Long? = null, var count: Int = 0, var peak: Double = 0.0,
        var latched: Boolean = false, var lastEvent: Long? = null, var triggerCount: Int = 0,
        var direction: Int = 0) {
        fun clearEvidence() { start = null; count = 0; peak = 0.0; latched = false; triggerCount = 0; direction = 0 }
    }
    private val windows = mutableMapOf<DrivingEventType, Window>()
    private var previousTimestamp: Long? = null
    fun reset() { windows.clear(); previousTimestamp = null }

    /** Read-only count of samples currently above the trigger; does not change detector decisions. */
    fun consecutiveTriggers(type: DrivingEventType): Int = windows[type]?.triggerCount ?: 0

    /** Read-only temporal evidence for the experimental UI; does not change detector decisions. */
    fun evidence(type: DrivingEventType, now: Long): String {
        val window = windows[type] ?: return "Sin evidencia"
        val criteria = configuration?.let { when(type) {
            DrivingEventType.HARSH_ACCELERATION -> it.acceleration
            DrivingEventType.HARSH_BRAKING -> it.braking
            DrivingEventType.SHARP_TURN -> it.turn
        } }
        val cooldown = window.lastEvent?.let { (criteria?.cooldownMillis ?: 0) - (now - it) }?.coerceAtLeast(0) ?: 0
        return "${window.triggerCount} muestras sobre umbral · ${window.start?.let { now - it } ?: 0} ms · " +
            if (window.latched) "candidato retenido hasta liberación" else if(cooldown > 0) "cooldown: $cooldown ms" else "evaluando duración / liberación"
    }

    fun detect(sample: ProcessedImuSample): List<CandidateEvent> {
        val config = configuration ?: return emptyList()
        val rest = config.restAccelerationG ?: return emptyList()
        val now = sample.receivedAtMillis
        val previous = previousTimestamp
        // A repeated/out-of-order reception cannot count twice; clock reversal resets temporal evidence.
        if (previous != null && now == previous) return emptyList()
        if (previous != null && now < previous) { reset(); previousTimestamp = now; return emptyList() }
        previousTimestamp = now
        fun axis(vector: Vector3, selected: ImuAxis) = when (selected) { ImuAxis.X -> vector.x; ImuAxis.Y -> vector.y; ImuAxis.Z -> vector.z }
        val longitudinal = axis(sample.accelerationG - rest, config.longitudinalAxis) * config.forwardSign
        val turnRate = axis(sample.angularVelocityDps, config.turnAxis)
        val signals = listOf(
            Triple(DrivingEventType.HARSH_ACCELERATION, longitudinal, config.acceleration),
            Triple(DrivingEventType.HARSH_BRAKING, -longitudinal, config.braking),
            Triple(DrivingEventType.SHARP_TURN, abs(turnRate), config.turn))
        return signals.mapNotNull { (type, value, criteria) ->
            val window = windows.getOrPut(type) { Window() }
            if (previous != null && now - previous > criteria.maximumGapMillis) {
                window.clearEvidence()
            }
            if (!window.latched && window.start?.let { start ->
                    criteria.maximumWindowMillis?.let { now - start > it } == true
                } == true) window.clearEvidence() // Old isolated peaks cannot accumulate indefinitely.
            if (value <= criteria.release) {
                window.clearEvidence()
                return@mapNotNull null
            }
            val direction = if (turnRate >= 0) 1 else -1
            if (type == DrivingEventType.SHARP_TURN && window.start != null && window.direction != direction) {
                // Alternating signed yaw spikes cannot accumulate as a sustained turn; retain cooldown.
                window.clearEvidence()
            }
            if (window.latched || window.lastEvent?.let { now - it < criteria.cooldownMillis } == true) return@mapNotNull null
            if (window.start == null) {
                if (value < criteria.trigger) return@mapNotNull null
                window.start = now
                window.direction = direction
            }
            window.count++
            if (value >= criteria.trigger) window.triggerCount++
            window.peak = maxOf(window.peak, value)
            val start = checkNotNull(window.start)
            if (window.triggerCount < criteria.minimumSamples || now - start < criteria.minimumDurationMillis) return@mapNotNull null
            window.latched = true; window.lastEvent = now
            CandidateEvent(type, start, now, window.count, window.peak, config.status)
        }
    }
}
