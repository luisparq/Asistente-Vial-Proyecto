package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.diagnostic.RawImuSample
import com.tecsup.asistente.conduccion.domain.processing.ExperimentalCalibrationAccumulator
import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

/** Synthetic TEST fixtures, not physical calibration measurements. */
class ExperimentalCalibrationTest {
    @Test fun calculatesSixAxisMeansSampleDeviationAndNominalGyroOffsets() {
        val accumulator = ExperimentalCalibrationAccumulator()
        accumulator.add(RawImuSample(0, 10, -10, 131, -262, 393))
        accumulator.add(RawImuSample(2, 12, -8, 393, 0, 655))
        val result = accumulator.result()!!
        assertEquals(2, result.validSamples)
        val means = listOf(1.0, 11.0, -9.0, 262.0, -131.0, 524.0)
        result.raw.forEachIndexed { axis, stats ->
            assertEquals(means[axis], stats.mean, 1e-10)
            assertEquals(if (axis < 3) sqrt(2.0) else sqrt(34322.0), stats.sampleStandardDeviation!!, 1e-10)
            val scale = if (axis < 3) 16384.0 else 131.0
            assertEquals(stats.mean / scale, result.nominal[axis].mean, 1e-10)
            assertEquals(stats.sampleStandardDeviation / scale, result.nominal[axis].sampleStandardDeviation!!, 1e-10)
        }
        assertEquals(2.0, result.estimatedGyroOffsetsDps.x, 0.0)
        assertEquals(-1.0, result.estimatedGyroOffsetsDps.y, 0.0)
        assertEquals(4.0, result.estimatedGyroOffsetsDps.z, 0.0)
    }

    @Test fun constantSeriesHasZeroDeviationAndDoesNotInventMissingResults() {
        val accumulator = ExperimentalCalibrationAccumulator()
        assertNull(accumulator.result())
        accumulator.add(RawImuSample(100, 100, 100, -131, -131, -131))
        assertNull(accumulator.result()!!.raw[0].sampleStandardDeviation)
        repeat(119) { accumulator.add(RawImuSample(100, 100, 100, -131, -131, -131)) }
        assertEquals(120, accumulator.validSamples)
        assertEquals(0.0, accumulator.result()!!.raw[3].sampleStandardDeviation!!, 0.0)
    }

    @Test fun invalidAxesDoNotContributeToCountOrStatistics() {
        val accumulator = ExperimentalCalibrationAccumulator()
        assertFalse(accumulator.add(RawImuSample(40000, 0, 0, 0, 0, 0)))
        assertEquals(0, accumulator.validSamples)
        assertNull(accumulator.result())
    }
}
