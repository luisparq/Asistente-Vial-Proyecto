package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.data.ble.*
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import com.tecsup.asistente.conduccion.domain.processing.ManeuverDemoTestOnly
import com.tecsup.asistente.conduccion.feature.diagnostic.DiagnosticViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DemonstrativeTestFlowTest {
    private val restFrame = rawFrame()
    private val turnFrame = rawFrame(gz = 6000)    // 45.8 °/s nominal.
    private val accelFrame = rawFrame(ax = 4500)   // +0.27 g nominal.
    private val brakeFrame = rawFrame(ax = -4500)  // -0.27 g nominal.

    private fun rawFrame(ax: Short = 0, az: Short = 16384, gz: Short = 0): ByteArray =
        ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(ax); putShort(0); putShort(az); putShort(0); putShort(0); putShort(gz)
        }.array()

    private class Fixture(val diagnostic: DiagnosticViewModel, val transport: TestGattTransport)

    private fun demoFlowTest(body: suspend TestScope.(Fixture) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val platform = TestBlePlatform()
            val source = RawSensorDataSource(backgroundScope)
            val transport = TestGattTransport()
            val connector = GattBleConnection(transport, source, platform, backgroundScope)
            val repository = DefaultBleRepository(platform, connector, backgroundScope)
            val diagnostic = DiagnosticViewModel(repository, source, clock = schedulerClock())
            store.put("diagnostic", diagnostic)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { diagnostic.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { diagnostic.laboratory.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { diagnostic.demo.collect {} }

            connector.select(BleDevice("fixture-esp32", "AsistenteVial-IMU"))
            connector.prepare()
            runCurrent()
            transport.latest.listener.connected(); runCurrent()
            transport.latest.listener.services(validProfile); runCurrent()
            transport.latest.listener.subscribed(); runCurrent()
            transport.latest.listener.frame(restFrame); runCurrent()
            assertEquals(ConnectionStatus.CONNECTED, diagnostic.state.value.connection.status)
            assertEquals(SensorDataStatus.RECEIVING, diagnostic.state.value.sensorStatus)

            diagnostic.startCharts(); runCurrent()
            diagnostic.startDemoTest(); runCurrent()
            assertTrue(diagnostic.demo.value.active)
            advanceTimeBy(250)
            val fixture = Fixture(diagnostic, transport)
            repeat(ManeuverDemoTestOnly.REST_SAMPLES_TEST_ONLY) { feed(fixture, restFrame) }
            assertTrue(diagnostic.demo.value.restReady)
            body(fixture)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.feed(fixture: Fixture, frame: ByteArray) {
        fixture.transport.latest.listener.frame(frame); runCurrent()
        advanceTimeBy(250); runCurrent()
    }

    private fun TestScope.schedulerClock(): Clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(testScheduler.currentTime)
    }

    @Test
    fun alertAndVoiceTextComeFirstAndBuzzerDoneOnlyConfirmsTheCommand() = demoFlowTest { fixture ->
        val diagnostic = fixture.diagnostic
        feed(fixture, turnFrame)
        assertEquals(1, diagnostic.demo.value.consecutiveSamples)
        assertFalse(diagnostic.demo.value.alertVisible)
        assertTrue(fixture.transport.latest.buzzerWrites.isEmpty())

        feed(fixture, turnFrame)
        with(diagnostic.demo.value) {
            assertTrue("La alerta no espera a BUZZER_DONE", alertVisible)
            assertTrue(buzzerPending)
            assertFalse(buzzerDoneConfirmed)
            assertEquals("Giro brusco detectado", alertTitle)
            assertEquals("Ey, noté un giro brusco. Vamos con calma.", alertMessage)
            assertTrue(alertObserved, alertObserved.contains("GIRO_BRUSCO_TEST"))
            assertEquals("|gz| > 45.0 °/s", alertThreshold)
        }
        assertEquals(1, fixture.transport.latest.buzzerWrites.size)

        repeat(2) { feed(fixture, turnFrame) } // Sigue pendiente: sin comandos duplicados.
        assertEquals(1, fixture.transport.latest.buzzerWrites.size)

        fixture.transport.latest.listener.buzzerDone(); runCurrent()
        assertFalse(diagnostic.demo.value.buzzerPending)
        assertTrue(diagnostic.demo.value.buzzerDoneConfirmed)
        assertTrue(diagnostic.demo.value.alertVisible)

        diagnostic.dismissDemoAlert(); runCurrent()
        assertFalse(diagnostic.demo.value.alertVisible)
        diagnostic.stopDemoTest(); runCurrent()
        assertFalse(diagnostic.demo.value.active)
    }

    @Test
    fun accelerationAndBrakingUseTheirOwnTextAfterTheCooldown() = demoFlowTest { fixture ->
        val diagnostic = fixture.diagnostic
        repeat(2) { feed(fixture, accelFrame) }
        assertEquals("Aceleración brusca detectada", diagnostic.demo.value.alertTitle)
        assertEquals("Ey, noté una aceleración brusca. Acelera con suavidad.", diagnostic.demo.value.alertMessage)
        assertEquals("ax corregido > +0.25 g", diagnostic.demo.value.alertThreshold)
        fixture.transport.latest.listener.buzzerDone(); runCurrent()
        diagnostic.dismissDemoAlert(); runCurrent()

        repeat(4) { feed(fixture, restFrame) }
        repeat(2) { feed(fixture, brakeFrame) } // Aún dentro de los 3 s.
        assertFalse(diagnostic.demo.value.alertVisible)
        repeat(9) { feed(fixture, restFrame) }
        repeat(2) { feed(fixture, brakeFrame) }
        assertEquals("Frenado brusco detectado", diagnostic.demo.value.alertTitle)
        assertEquals("Ey, noté un frenado brusco. Mantengamos una conducción suave.", diagnostic.demo.value.alertMessage)
        assertEquals("ax corregido < -0.25 g", diagnostic.demo.value.alertThreshold)
        assertEquals(2, fixture.transport.latest.buzzerWrites.size)
    }

    @Test
    fun missingBuzzerConfirmationExpiresAndIsReported() = demoFlowTest { fixture ->
        val diagnostic = fixture.diagnostic
        repeat(2) { feed(fixture, turnFrame) }
        assertTrue(diagnostic.demo.value.buzzerPending)

        repeat(8) { feed(fixture, restFrame) } // Mantiene viva la recepción IMU durante el timeout.
        assertFalse(diagnostic.demo.value.buzzerPending)
        assertFalse(diagnostic.demo.value.buzzerDoneConfirmed)
        assertEquals("Sin confirmación", diagnostic.demo.value.buzzerCommand)
        assertTrue(diagnostic.demo.value.statusText.contains("Sin confirmación BUZZER_DONE"))
    }

    @Test
    fun disconnectionResetsCountersAndIgnoresALateBuzzerDone() = demoFlowTest { fixture ->
        val diagnostic = fixture.diagnostic
        repeat(2) { feed(fixture, turnFrame) }
        assertTrue(diagnostic.demo.value.buzzerPending)

        fixture.transport.latest.listener.disconnected(); runCurrent()
        with(diagnostic.demo.value) {
            assertFalse(active)
            assertFalse(alertVisible)
            assertFalse(buzzerPending)
            assertFalse(restReady)
            assertEquals(0, consecutiveSamples)
            assertEquals(0L, cooldownRemainingMillis)
        }
        fixture.transport.latest.listener.buzzerDone(); runCurrent()
        assertFalse("Sin confirmación falsa tras desconectar", diagnostic.demo.value.buzzerDoneConfirmed)
    }

    @Test
    fun anIncompleteFrameCancelsTheDemoWithoutAnAlert() = demoFlowTest { fixture ->
        val diagnostic = fixture.diagnostic
        feed(fixture, turnFrame)
        feed(fixture, ByteArray(6))
        feed(fixture, turnFrame)
        assertFalse(diagnostic.demo.value.active)
        assertFalse(diagnostic.demo.value.alertVisible)
        assertTrue(fixture.transport.latest.buzzerWrites.isEmpty())
    }
}
