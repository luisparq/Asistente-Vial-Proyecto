package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.processing.ManeuverDemoReading
import com.tecsup.asistente.conduccion.domain.processing.ManeuverDemoTestOnly
import com.tecsup.asistente.conduccion.domain.processing.ManeuverDemoTracker
import com.tecsup.asistente.conduccion.domain.processing.ProcessedImuSample
import com.tecsup.asistente.conduccion.domain.processing.Vector3
import org.junit.Assert.*
import org.junit.Test

class ManeuverDemoTrackerTest {
    private val tracker = ManeuverDemoTracker()
    private var clock = 0L

    private fun feed(ax: Double = 0.0, gz: Double = 0.0, blocked: Boolean = false, stepMillis: Long = 250): ManeuverDemoReading =
        tracker.accept(ProcessedImuSample(Vector3(ax, 0.0, 1.0), Vector3(0.0, 0.0, gz), clock, false), blocked)
            .also { clock += stepMillis }

    private fun rest(ax: Double = 0.0) = repeat(ManeuverDemoTestOnly.REST_SAMPLES_TEST_ONLY) { feed(ax = ax) }

    @Test
    fun eachPatternNeedsTwoConsecutiveSamplesStrictlyAboveItsThreshold() {
        data class Case(val ax: Double, val gz: Double, val type: DrivingEventType, val observed: Double)
        listOf(
            Case(0.0, -46.0, DrivingEventType.SHARP_TURN, 46.0),
            Case(0.3, 0.0, DrivingEventType.HARSH_ACCELERATION, 0.3),
            Case(-0.3, 0.0, DrivingEventType.HARSH_BRAKING, -0.3),
        ).forEach { case ->
            tracker.reset()
            rest()
            assertNull(feed(case.ax, case.gz).detection)
            val detection = feed(case.ax, case.gz).detection
            assertEquals(case.type, detection?.type)
            assertEquals(case.observed, detection!!.observed, 1e-9)
        }
    }

    @Test
    fun exactThresholdValuesDoNotAlert() {
        rest()
        repeat(4) { assertNull(feed(gz = 45.0).detection) }
        repeat(4) { assertNull(feed(ax = 0.25).detection) }
        repeat(4) { assertNull(feed(ax = -0.25).detection) }
    }

    @Test
    fun onlyTheAverageObservedAtRestIsSubtractedFromAx() {
        rest(ax = 0.1)
        assertEquals(0.2, feed(ax = 0.3).axCorrectedG!!, 1e-9)
        assertNull(feed(ax = 0.3).detection) // +0.20 g corregido sigue bajo el umbral.
        feed(ax = 0.1)
        feed(ax = 0.4)
        assertEquals(DrivingEventType.HARSH_ACCELERATION, feed(ax = 0.4).detection?.type)
    }

    @Test
    fun noDetectionWhileRestIsBeingMeasuredAndMovementRestartsIt() {
        repeat(5) { feed() }
        val moved = feed(gz = 100.0)
        assertFalse(moved.restReady)
        assertEquals(0, moved.restSamples)
        repeat(ManeuverDemoTestOnly.REST_SAMPLES_TEST_ONLY - 1) { assertFalse(feed().restReady) }
        assertTrue(feed().restReady)
    }

    @Test
    fun aSampleBelowThresholdBreaksTheConsecutiveCount() {
        rest()
        assertEquals(1, feed(gz = 50.0).consecutiveSamples)
        assertEquals(0, feed(gz = 10.0).consecutiveSamples)
        assertNull(feed(gz = 50.0).detection)
        assertNotNull(feed(gz = 50.0).detection)
    }

    @Test
    fun aLostSampleBreaksTheConsecutiveCount() {
        rest()
        assertNull(feed(gz = 50.0, stepMillis = 500).detection) // Falta la muestra intermedia.
        assertNull(feed(gz = 50.0).detection)
        assertNotNull(feed(gz = 50.0).detection)
    }

    @Test
    fun cooldownIsThreeSecondsAndSharedBetweenPatterns() {
        rest()
        feed(gz = 50.0)
        val first = feed(gz = 50.0)
        assertNotNull(first.detection)
        assertEquals(ManeuverDemoTestOnly.COOLDOWN_TEST_ONLY_MILLIS, first.cooldownRemainingMillis)
        feed() // Libera el giro.
        assertNull(feed(ax = 0.4).detection) // Otro patrón dentro del cooldown.
        assertNull(feed(ax = 0.4).detection)
        feed()
        while (feed().cooldownRemainingMillis > 0) Unit
        feed(gz = 50.0)
        assertNotNull(feed(gz = 50.0).detection)
    }

    @Test
    fun aSustainedMovementAlertsOnlyOnceUntilItIsReleased() {
        rest()
        feed(gz = 50.0)
        assertNotNull(feed(gz = 50.0).detection)
        repeat(20) { assertNull(feed(gz = 50.0).detection) }
    }

    @Test
    fun noAlertStartsWhileBlockedByPendingConfirmationOrOpenAlert() {
        rest()
        feed(gz = 50.0, blocked = true)
        assertNull(feed(gz = 50.0, blocked = true).detection)
        assertEquals(0L, feed(blocked = true).cooldownRemainingMillis)
    }

    @Test
    fun resetClearsCountersCooldownAndRestReference() {
        rest()
        feed(gz = 50.0)
        tracker.reset()
        assertFalse(feed(gz = 50.0).restReady)
        rest()
        assertNull(feed(gz = 50.0).detection) // La muestra previa a reset no cuenta.
        assertNotNull(feed(gz = 50.0).detection)
    }
}
