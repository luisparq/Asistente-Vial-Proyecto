package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.feature.diagnostic.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Synthetic samples exercise persistence and lifecycle rules, not physical sensor accuracy. */
class FieldRecordingTest {
    @get:Rule val temp = TemporaryFolder()
    private val rest = RawImuSample(100, 200, 16384, -160, 150, -30)
    private fun calibrated(): FieldRecording = FieldRecording(temp.newFolder()).apply {
        begin(true, 0, "TEST", "X adelante Y izquierda Z arriba")
        repeat(120) { i -> sample(rest, GpsTelemetry(), i*250L, 1000+i*250L) }
        tick(30_000)
        assertNotNull(state.reference)
    }

    @Test fun stableRestThenTripPreservesRawAndSubtractsOnlyReferenceColumns() {
        val r = calibrated()
        r.begin(false, 31_000, "TEST", "X adelante Y izquierda Z arriba")
        r.label(FieldLabel.BRAKING)
        r.sample(rest, GpsTelemetry(GpsStatus.VALID, -16.0, -71.0, ageMillis = 100,
            receivedAtMonotonicMillis = 31_000), 31_250, 100_000)
        r.finish("Fin", 32_000)
        val lines = r.state.files.first { it.name.contains("recorrido") }.readLines()
        val columns = lines[1].split(',').map { it.trim('"') }
        assertEquals(FieldRecording.HEADER.split(',').size, columns.size)
        assertEquals("BRAKING", columns[7])
        assertEquals("16384", columns[10])
        assertEquals("1.0", columns[16]) // Gravity retained in nominal acceleration.
        assertEquals("0.0", columns[28]) // Difference from rest, NOT gravity compensation.
        assertEquals("0.0", columns[29])
        assertEquals("350", columns[35])
        assertTrue(lines.last().startsWith("\"END\""))
    }
    @Test fun cancelledOrInterruptedRestCannotEnableTripAndFilesRemain() {
        val r = FieldRecording(temp.newFolder())
        r.begin(true, 0, "TEST", "fixed")
        repeat(5) { r.sample(rest, GpsTelemetry(), it*250L, it*250L) }
        r.finish("Cancelada", 1300, invalidate = true)
        assertNull(r.state.reference)
        assertEquals(1, r.state.files.size)
        val recovered = FieldRecording(r.state.files.first().parentFile!!)
        recovered.refresh()
        assertEquals(1, recovered.state.files.size)
        assertNull(recovered.state.reference)
    }
    @Test fun staleGpsCoordinatesAreExcludedAndGapIsExplicit() {
        val r = calibrated()
        r.begin(false, 31_000, "TEST", "X adelante Y izquierda Z arriba")
        val gps = GpsTelemetry(GpsStatus.VALID, -16.0, -71.0, ageMillis = 100,
            receivedAtMonotonicMillis = 20_000)
        r.sample(rest, gps, 31_250, 100_000)
        r.sample(rest, gps, 32_250, 101_000)
        assertEquals(1, r.state.gaps)
        r.finish("BLE perdida", 32_500, invalidate = true)
        assertNull(r.state.reference)
        val row = r.state.files.first { it.name.contains("recorrido") }.readLines()[1].split(',').map { it.trim('"') }
        assertEquals("STALE", row[32])
        assertEquals("", row[33])
        assertEquals("", row[34])
    }
    @Test fun unstableRestOrMissingFramesNeverProducesReference() {
        val r = FieldRecording(temp.newFolder())
        r.begin(true, 0, "TEST", "fixed")
        repeat(120) { i -> r.sample(rest.copy(ax = if (i%2 == 0) 0 else 16000), GpsTelemetry(), i*250L, i*250L) }
        r.tick(30_000)
        assertNull(r.state.reference)
        r.begin(true, 31_000, "TEST", "fixed")
        r.tick(33_250)
        assertEquals(FieldPhase.IDLE, r.state.phase)
        assertNull(r.state.reference)
    }
    @Test fun changedDeviceInvalidatesReferenceAndInterruptedFileSurvives() {
        val r = calibrated()
        r.checkDevice("OTHER")
        assertNull(r.state.reference)
        r.begin(true, 40_000, "OTHER", "fixed")
        r.sample(rest, GpsTelemetry(), 40_250, 100_000)
        r.abandon() // Simulated process interruption: no END marker.
        val file = r.state.files.first { it.name.startsWith("40000_") }
        assertTrue(file.readText().contains("SAMPLE"))
        assertFalse(file.readText().contains("\"END\""))
    }
}
