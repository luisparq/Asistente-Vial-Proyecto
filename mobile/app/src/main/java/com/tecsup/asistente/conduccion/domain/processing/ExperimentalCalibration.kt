package com.tecsup.asistente.conduccion.domain.processing

import com.tecsup.asistente.conduccion.domain.diagnostic.RawImuSample
import kotlin.math.sqrt

data class AxisStatistics(val mean: Double, val sampleStandardDeviation: Double?)
data class ExperimentalCalibrationResult(
    val validSamples: Int,
    /** ax, ay, az, gx, gy, gz; raw counts and nominal units respectively. */
    val raw: List<AxisStatistics>,
    val nominal: List<AxisStatistics>,
    val estimatedGyroOffsetsDps: Vector3,
)

/** In-memory Welford statistics. No calibration is applied or saved. */
class ExperimentalCalibrationAccumulator {
    private val converter = ImuConverter()
    private val rawMeans = DoubleArray(6)
    private val rawM2 = DoubleArray(6)
    private val nominalMeans = DoubleArray(6)
    private val nominalM2 = DoubleArray(6)
    var validSamples: Int = 0
        private set

    fun add(sample: RawImuSample): Boolean {
        val counts = listOf(sample.ax, sample.ay, sample.az, sample.gx, sample.gy, sample.gz)
        if (counts.any { it !in Short.MIN_VALUE..Short.MAX_VALUE }) return false
        val converted = converter.convert(sample)
        val nominal = listOf(converted.accelerationG.x, converted.accelerationG.y, converted.accelerationG.z,
            converted.angularVelocityDps.x, converted.angularVelocityDps.y, converted.angularVelocityDps.z)
        validSamples++
        fun update(values: List<Double>, means: DoubleArray, m2: DoubleArray) {
            values.forEachIndexed { axis, value ->
                val delta = value - means[axis]
                means[axis] += delta / validSamples
                m2[axis] += delta * (value - means[axis])
            }
        }
        update(counts.map(Int::toDouble), rawMeans, rawM2)
        update(nominal, nominalMeans, nominalM2)
        return true
    }

    fun result(): ExperimentalCalibrationResult? {
        if (validSamples == 0) return null
        fun statistics(means: DoubleArray, m2: DoubleArray) = means.indices.map { axis ->
            AxisStatistics(means[axis], if (validSamples < 2) null else sqrt(maxOf(0.0, m2[axis] / (validSamples - 1))))
        }
        return ExperimentalCalibrationResult(validSamples, statistics(rawMeans, rawM2), statistics(nominalMeans, nominalM2),
            Vector3(nominalMeans[3], nominalMeans[4], nominalMeans[5]))
    }
}
