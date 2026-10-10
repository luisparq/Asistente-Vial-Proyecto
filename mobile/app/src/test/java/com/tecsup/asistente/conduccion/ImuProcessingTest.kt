package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.domain.processing.*
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import org.junit.Assert.*
import org.junit.Test

/** Synthetic TEST ONLY sequences. These tests never validate physical driving maneuvers. */
class ImuProcessingTest {
    // TEST ONLY orientation, thresholds and timing; not shipped as production configuration.
    private val criteria = CandidateCriteria(1.0, 0.5, 500, 3, 300, 1000, maximumWindowMillis = 1000)
    private val config = DetectorConfiguration(ImuAxis.X, 1, ImuAxis.Z, criteria, criteria, criteria, CriteriaStatus.TEST_ONLY,
        restAccelerationG = Vector3(0.0, 0.0, 1.0)) // TEST ONLY rest/gravity orientation.
    private fun sample(time: Long, accel: Double = 0.0, gyro: Double = 0.0) =
        ProcessedImuSample(Vector3(accel, 0.0, 0.0), Vector3(0.0, 0.0, gyro), time, false)

    @Test fun conversionMatchesFirmwareRangeAndKeepsReceptionTimestamp() {
        val raw = RawImuSample(16384, -16384, 8192, 131, -262, 0, 1234)
        val converted = ImuConverter().convert(raw)
        assertEquals(Vector3(1.0, -1.0, 0.5), converted.accelerationG)
        assertEquals(Vector3(1.0, -2.0, 0.0), converted.angularVelocityDps)
        assertEquals(1234L, converted.receivedAtMillis)
        assertEquals(1234L, ImuRawDecoder.decode(ByteArray(12), 1234).receivedAtMillis)
    }

    @Test fun absenceOfCalibrationAndFilterPreservesConvertedSignalAndDisablesCandidates() {
        val pipeline = ImuPipeline()
        val result = pipeline.accept(RawImuSample(16384, 0, 0, 131, 0, 0, 1000))
        assertEquals(result.converted.accelerationG, result.processed.accelerationG)
        assertEquals(result.converted.angularVelocityDps, result.processed.angularVelocityDps)
        assertFalse(result.processed.hasCalibrationOffsets)
        assertTrue(result.candidates.isEmpty())
        assertFalse(DrivingEventDetector().enabled)
    }

    @Test fun configuredOffsetsAreSubtractedInPhysicalUnitsBeforeSmoothing() {
        val processor = ImuProcessor(ImuCalibration(Vector3(0.1, 0.2, 0.3), Vector3(1.0, 2.0, 3.0)))
        val output = processor.process(PhysicalImuSample(Vector3(1.1, 2.2, 3.3), Vector3(11.0, 22.0, 33.0), 0))
        assertEquals(1.0, output.accelerationG.x, 1e-10)
        assertEquals(2.0, output.accelerationG.y, 1e-10)
        assertEquals(3.0, output.accelerationG.z, 1e-10)
        assertEquals(Vector3(10.0, 20.0, 30.0), output.angularVelocityDps)
        assertTrue(output.hasCalibrationOffsets)
    }

    @Test fun optionalTestFilterAndResetDoNotKeepOldConnectionState() {
        val processor = ImuProcessor(smoothingAlpha = 0.5) // TEST ONLY alpha
        fun input(value: Double, time: Long) = PhysicalImuSample(Vector3(value, value, value), Vector3(value, value, value), time)
        assertEquals(0.0, processor.process(input(0.0, 0)).accelerationG.x, 0.0)
        assertEquals(2.0, processor.process(input(4.0, 250)).accelerationG.x, 0.0)
        processor.reset()
        assertEquals(4.0, processor.process(input(4.0, 500)).accelerationG.x, 0.0)
    }

    @Test fun singleSpikeAndInsufficientDurationNeverProduceCandidates() {
        val detector = DrivingEventDetector(config)
        assertTrue(detector.detect(sample(0, 2.0)).isEmpty())
        assertTrue(detector.detect(sample(250, 0.0)).isEmpty())
        assertTrue(detector.detect(sample(500, 2.0)).isEmpty())
        assertTrue(detector.detect(sample(750, 2.0)).isEmpty())
    }

    @Test fun controlledWindowsProduceOnlyTestCandidatesForEachPreliminaryType() {
        for ((accel, gyro, type) in listOf(Triple(2.0, 0.0, DrivingEventType.HARSH_ACCELERATION),
            Triple(-2.0, 0.0, DrivingEventType.HARSH_BRAKING), Triple(0.0, -2.0, DrivingEventType.SHARP_TURN))) {
            val detector = DrivingEventDetector(config)
            assertTrue(detector.detect(sample(0, accel, gyro)).isEmpty())
            assertTrue(detector.detect(sample(250, accel, gyro)).isEmpty())
            val candidate = detector.detect(sample(500, accel, gyro)).single()
            assertEquals(type, candidate.type)
            assertEquals(CriteriaStatus.TEST_ONLY, candidate.criteriaStatus)
            assertEquals(3, candidate.sampleCount)
        }
    }

    @Test fun hysteresisLatchAndCooldownAvoidRepeatedCandidates() {
        val detector = DrivingEventDetector(config)
        listOf(0L, 250L, 500L).forEach { detector.detect(sample(it, 2.0)) }
        assertTrue(detector.detect(sample(750, 2.0)).isEmpty())
        detector.detect(sample(1000, 0.0)) // Release latch, retain cooldown.
        assertTrue(detector.detect(sample(1250, 2.0)).isEmpty())
        assertTrue(detector.detect(sample(1500, 2.0)).isEmpty())
        assertTrue(detector.detect(sample(1750, 2.0)).isEmpty())
        assertEquals(1, detector.detect(sample(2000, 2.0)).size)
        assertTrue(detector.detect(sample(2250, 0.8)).isEmpty()) // Hysteresis retains latch above release.
    }

    @Test fun duplicatesGapsClockReversalAndExplicitResetCannotReuseAWindow() {
        val detector = DrivingEventDetector(config)
        detector.detect(sample(0, 2.0))
        assertTrue(detector.detect(sample(0, 2.0)).isEmpty())
        detector.detect(sample(250, 2.0))
        assertTrue(detector.detect(sample(1000, 2.0)).isEmpty()) // Gap resets count/duration.
        assertTrue(detector.detect(sample(900, 2.0)).isEmpty()) // Clock reversal clears evidence.
        detector.reset()
        assertTrue(detector.detect(sample(2000, 2.0)).isEmpty())
    }

    @Test fun invalidParametersCannotSilentlyEnableDetectionOrFiltering() {
        assertThrows(IllegalArgumentException::class.java) { ImuProcessor(smoothingAlpha = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { CandidateCriteria(1.0, 0.5, 0, 1, 300, 1000) }
        assertThrows(IllegalArgumentException::class.java) { Vector3(Double.NaN, 0.0, 0.0) }
    }

    @Test fun explicitRestOrientationPreventsGravityFromBecomingAccelerationOrBraking() {
        val rest = Vector3(0.0, 0.0, 1.0) // Synthetic TEST gravity aligned with selected longitudinal Z.
        for (sign in listOf(-1, 1)) {
            val detector = DrivingEventDetector(config.copy(longitudinalAxis = ImuAxis.Z, forwardSign = sign, restAccelerationG = rest))
            repeat(20) { index ->
                val noise = if (index % 2 == 0) 0.05 else -0.05 // TEST ONLY noise.
                assertTrue(detector.detect(ProcessedImuSample(Vector3(noise, noise, 1.0 + noise),
                    Vector3(noise, noise, noise), index * 250L, false)).isEmpty())
            }
        }
        val missingReference = DrivingEventDetector(config.copy(restAccelerationG = null))
        assertFalse(missingReference.enabled)
        repeat(4) { assertTrue(missingReference.detect(sample(it * 250L, 2.0, 2.0)).isEmpty()) }
    }

    @Test fun oneHighPeakFollowedBySubtriggerNoiseDoesNotConfirmAManeuver() {
        val detector = DrivingEventDetector(config)
        assertTrue(detector.detect(sample(0, 2.0)).isEmpty())
        repeat(10) { assertTrue(detector.detect(sample((it + 1) * 250L, 0.8)).isEmpty()) }
        val sporadic = DrivingEventDetector(config)
        repeat(24) { index ->
            assertTrue(sporadic.detect(sample(index * 250L, if (index % 8 == 0) 2.0 else 0.8)).isEmpty())
        }
    }

    @Test fun alternatingTurnNoiseCannotFormASustainedCandidate() {
        val detector = DrivingEventDetector(config)
        repeat(20) { index ->
            assertTrue(detector.detect(sample(index * 250L, gyro = if (index % 2 == 0) 2.0 else -2.0)).isEmpty())
        }
    }

    @Test fun pipelineCombinesTestOffsetsFilterOrientationAndAllThreeCandidateTypes() {
        val rest = Vector3(0.0, 1.0, 0.0)
        val bias = ImuCalibration(Vector3(0.1, 0.1, 0.1), Vector3(1.0, 1.0, 1.0)) // TEST ONLY offsets.
        val pipelineCriteria = CandidateCriteria(0.75, 0.25, 500, 3, 300, 1000, maximumWindowMillis = 1000) // TEST ONLY.
        for (type in listOf(DrivingEventType.HARSH_ACCELERATION, DrivingEventType.HARSH_BRAKING, DrivingEventType.SHARP_TURN)) {
            val pipeline = ImuPipeline(processor = ImuProcessor(bias, smoothingAlpha = 0.5),
                detector = DrivingEventDetector(config.copy(longitudinalAxis = ImuAxis.Y, forwardSign = -1,
                    turnAxis = ImuAxis.X, restAccelerationG = rest, acceleration = pipelineCriteria,
                    braking = pipelineCriteria, turn = pipelineCriteria)))
            var candidates = emptyList<CandidateEvent>()
            repeat(4) { index ->
                val y = when (type) { DrivingEventType.HARSH_ACCELERATION -> -16384; DrivingEventType.HARSH_BRAKING -> 32700; else -> 18022 }
                val gx = if (type == DrivingEventType.SHARP_TURN) -262 else 131
                val result = pipeline.accept(RawImuSample(1638, y, 1638, gx, 131, 131, index * 250L))
                assertTrue(result.processed.hasCalibrationOffsets)
                candidates = candidates + result.candidates
            }
            assertEquals(type, candidates.single().type)
            assertEquals(CriteriaStatus.TEST_ONLY, candidates.single().criteriaStatus)
        }
    }

    @Test fun replayedTimestampDoesNotApplyTheFilterTwice() {
        val processor = ImuProcessor(smoothingAlpha = 0.5)
        fun input(value: Double, time: Long) = PhysicalImuSample(Vector3(value, value, value), Vector3(value, value, value), time)
        processor.process(input(0.0, 0))
        val first = processor.process(input(4.0, 250))
        assertEquals(first, processor.process(input(4.0, 250)))
        assertEquals(3.0, processor.process(input(4.0, 500)).accelerationG.x, 0.0)
    }
}
