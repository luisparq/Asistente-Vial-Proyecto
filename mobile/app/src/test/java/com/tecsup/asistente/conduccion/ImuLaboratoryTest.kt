package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.processing.*
import com.tecsup.asistente.conduccion.domain.diagnostic.RawImuSample
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import org.junit.Assert.*
import org.junit.Test

/** Synthetic software fixtures, never physical calibration measurements. */
class ImuLaboratoryTest {
    private fun sample(time: Long) = PhysicalImuSample(Vector3(0.0, 0.0, 1.0), Vector3(0.0, 0.0, 0.0), time)
    @Test fun bufferBoundsTimeCapacityAndRejectsDuplicates() {
        val buffer = ImuChartBuffer()
        repeat(200) { buffer.add(sample(it * 250L)) }
        assertEquals(121, buffer.snapshot().size)
        assertEquals(30_000L, buffer.snapshot().last().receivedAtMillis - buffer.snapshot().first().receivedAtMillis)
        buffer.add(sample(49_750)); buffer.add(sample(0))
        assertEquals(121, buffer.snapshot().size)
        repeat(200) { buffer.add(sample(50_000 + it.toLong())) }
        assertEquals(128, buffer.snapshot().size)
        buffer.clear(); assertTrue(buffer.snapshot().isEmpty())
    }
    @Test fun syntheticPatternsProduceOnlyExpectedCandidate() {
        (listOf<DrivingEventType?>(null) + DrivingEventType.entries).forEach { type ->
            val detector = DrivingEventDetector(TestOnlyLaboratory.configuration())
            val processor = ImuProcessor()
            val candidates = TestOnlyLaboratory.synthetic(type).flatMap { detector.detect(processor.process(it)) }
            assertEquals(if(type == null) 0 else 1, candidates.size)
            if(type != null) {
                assertEquals(type, candidates.single().type)
                assertEquals(CriteriaStatus.TEST_ONLY, candidates.single().criteriaStatus)
            }
        }
    }
    @Test fun variabilityScreeningAndGyroOnlyCorrection() {
        val unstable = ExperimentalCalibrationAccumulator()
        repeat(120) { unstable.add(RawImuSample(if(it % 2 == 0) 10000 else -10000, 0, 16384, 131, 0, 0)) }
        assertFalse(unstable.result()!!.isStableForLab())
        val stable = ExperimentalCalibrationAccumulator()
        repeat(120) { stable.add(RawImuSample(0, 0, 16384, 131, -262, 393)) }
        val result = stable.result()!!
        assertTrue(result.isStableForLab())
        val original = ImuConverter().convert(RawImuSample(0, 0, 16384, 131, -262, 393))
        val corrected = ImuProcessor(ImuCalibration(Vector3(0.0, 0.0, 0.0), result.estimatedGyroOffsetsDps)).process(original)
        assertEquals(original.accelerationG, corrected.accelerationG)
        assertEquals(Vector3(0.0, 0.0, 0.0), corrected.angularVelocityDps)
    }
    @Test fun defaultDetectorRemainsDisabled() {
        val detector = DrivingEventDetector()
        assertFalse(detector.enabled)
        TestOnlyLaboratory.synthetic(DrivingEventType.SHARP_TURN).forEach {
            assertTrue(detector.detect(ImuProcessor().process(it)).isEmpty())
        }
        repeat(10) { assertEquals("Sin evidencia", detector.evidence(DrivingEventType.SHARP_TURN, 0)) }
    }
}
