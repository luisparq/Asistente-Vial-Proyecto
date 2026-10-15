package com.tecsup.asistente.conduccion.domain.processing

import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import kotlin.math.abs

/** Bounded reception-time history, never persisted; duplicate/out-of-order samples are ignored. */
class ImuChartBuffer(private val durationMillis: Long = 30_000, private val capacity: Int = 128) {
    init { require(durationMillis > 0 && capacity >= 2) }
    private val samples = ArrayDeque<PhysicalImuSample>()
    fun clear() = samples.clear()
    fun add(sample: PhysicalImuSample): List<PhysicalImuSample> {
        if (samples.lastOrNull()?.let { sample.receivedAtMillis <= it.receivedAtMillis } == true) return snapshot()
        samples.addLast(sample)
        while (samples.size > capacity || samples.first().receivedAtMillis < sample.receivedAtMillis - durationMillis) samples.removeFirst()
        return snapshot()
    }
    fun snapshot() = samples.toList()
}

/** Screening limits ONLY for laboratory variability, not validated physical calibration tolerances. */
fun ExperimentalCalibrationResult.isStableForLab(): Boolean = validSamples >= 100 && nominal.allIndexed { index, stats ->
    stats.sampleStandardDeviation?.let { it <= if (index < 3) 0.02 else 1.0 } == true
}
private fun <T> List<T>.allIndexed(predicate: (Int, T) -> Boolean) = indices.all { predicate(it, this[it]) }

object TestOnlyLaboratory {
    fun configuration(axis: ImuAxis = ImuAxis.X, sign: Int = 1, turnAxis: ImuAxis = ImuAxis.Z,
        rest: Vector3 = Vector3(0.0, 0.0, 1.0)) = DetectorConfiguration(axis, sign, turnAxis,
        CandidateCriteria(0.3, 0.1, 500, 3, 400, 1500, 2000),
        CandidateCriteria(0.3, 0.1, 500, 3, 400, 1500, 2000),
        CandidateCriteria(30.0, 10.0, 500, 3, 400, 1500, 2000), CriteriaStatus.TEST_ONLY, rest)
    fun synthetic(type: DrivingEventType?): List<PhysicalImuSample> = (0..15).map { index ->
        val active = index in 4..9
        val acceleration = if (active) when(type) {
            DrivingEventType.HARSH_ACCELERATION -> 0.5
            DrivingEventType.HARSH_BRAKING -> -0.5
            else -> 0.0
        } else 0.0
        PhysicalImuSample(Vector3(acceleration, 0.0, 1.0),
            Vector3(0.0, 0.0, if(active && type == DrivingEventType.SHARP_TURN) 45.0 else 0.0), index * 250L)
    }
    fun axis(vector: Vector3, axis: ImuAxis) = when(axis) { ImuAxis.X -> vector.x; ImuAxis.Y -> vector.y; ImuAxis.Z -> vector.z }
    fun signals(sample: ProcessedImuSample, config: DetectorConfiguration): List<Double> {
        val longitudinal = axis(sample.accelerationG - checkNotNull(config.restAccelerationG), config.longitudinalAxis) * config.forwardSign
        return listOf(longitudinal, -longitudinal, abs(axis(sample.angularVelocityDps, config.turnAxis)))
    }
}
