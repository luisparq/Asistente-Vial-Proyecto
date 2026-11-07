package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.data.ble.*
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
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

    private fun rawFrame(ax: Short = 0, ay: Short = 0, az: Short = 16384, gx: Short = 0, gy: Short = 0, gz: Short = 0): ByteArray {
        val buffer = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(ax)
        buffer.putShort(ay)
        buffer.putShort(az)
        buffer.putShort(gx)
        buffer.putShort(gy)
        buffer.putShort(gz)
        return buffer.array()
    }

    @Test
    fun completeDemonstrativeFlowFromThresholdExceededToBuzzerDoneAndAlert() = runTest {
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

            // Connect GATT
            val fixture = BleDevice("fixture-esp32", "AsistenteVial-IMU")
            connector.select(fixture)
            connector.prepare()
            runCurrent()
            transport.latest.listener.connected(); runCurrent()
            transport.latest.listener.services(validProfile); runCurrent()
            transport.latest.listener.subscribed(); runCurrent()

            // Feed rest sample
            transport.latest.listener.frame(rawFrame(az = 16384)); runCurrent()
            assertEquals(ConnectionStatus.CONNECTED, diagnostic.state.value.connection.status)
            assertEquals(SensorDataStatus.RECEIVING, diagnostic.state.value.sensorStatus)

            diagnostic.startCharts()
            runCurrent()

            // Start demonstrative test
            diagnostic.startDemoTest()
            runCurrent()
            assertTrue(diagnostic.demo.value.active)
            assertEquals(60, diagnostic.demo.value.remainingSeconds)
            assertFalse(diagnostic.demo.value.alertVisible)
            assertFalse(diagnostic.demo.value.buzzerPending)

            // Feed sharp turn exceeding 30 °/s:
            // 131 LSB / (°/s) nominal -> 45 °/s ≈ 5895
            val turnSample = rawFrame(az = 16384, gz = 6000)
            transport.latest.listener.frame(turnSample); runCurrent()
            advanceTimeBy(250)
            transport.latest.listener.frame(turnSample); runCurrent()

            // Android detects threshold and orders buzzer over BLE
            assertTrue("Buzzer should be pending confirmation", diagnostic.demo.value.buzzerPending)
            assertEquals(1, transport.latest.buzzerWrites.size)
            // Alert must NOT be shown yet before BUZZER_DONE
            assertFalse("Alert must not be visible before BUZZER_DONE", diagnostic.demo.value.alertVisible)

            // ESP32 confirms BUZZER_DONE
            transport.latest.listener.buzzerDone()
            runCurrent()

            // Now alert is visible!
            assertTrue("Alert must be visible after BUZZER_DONE", diagnostic.demo.value.alertVisible)
            assertFalse(diagnostic.demo.value.buzzerPending)
            assertEquals("Ey, noté un giro brusco. Vamos con calma", diagnostic.demo.value.alertMessage)

            // User dismisses alert
            diagnostic.dismissDemoAlert()
            runCurrent()
            assertFalse(diagnostic.demo.value.alertVisible)

            // Stop test
            diagnostic.stopDemoTest()
            runCurrent()
            assertFalse(diagnostic.demo.value.active)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private fun TestScope.schedulerClock(): Clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(testScheduler.currentTime)
    }

    @Test
    fun missingBuzzerConfirmationExpiresWithoutShowingAlert() = runTest {
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

            val fixture = BleDevice("fixture-esp32", "AsistenteVial-IMU")
            connector.select(fixture)
            connector.prepare()
            runCurrent()
            transport.latest.listener.connected(); runCurrent()
            transport.latest.listener.services(validProfile); runCurrent()
            transport.latest.listener.subscribed(); runCurrent()
            transport.latest.listener.frame(rawFrame(az = 16384)); runCurrent()

            diagnostic.startCharts()
            runCurrent()
            diagnostic.startDemoTest()
            runCurrent()

            val turnSample = rawFrame(az = 16384, gz = 6000)
            transport.latest.listener.frame(turnSample); runCurrent()
            advanceTimeBy(250)
            transport.latest.listener.frame(turnSample); runCurrent()
            assertTrue(diagnostic.demo.value.buzzerPending)

            // Keep IMU reception alive while the confirmation timeout elapses.
            advanceTimeBy(1_000)
            transport.latest.listener.frame(rawFrame(az = 16384)); runCurrent()
            // No BUZZER_DONE: Android must give up without showing a false alert.
            advanceTimeBy(1_000)
            runCurrent()
            assertFalse(diagnostic.demo.value.buzzerPending)
            assertFalse(diagnostic.demo.value.alertVisible)
            assertTrue(diagnostic.demo.value.statusText.contains("Sin confirmación BUZZER_DONE"))
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun disconnectionCancelsDemoTestImmediatelyWithoutFakingConfirmation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val platform = TestBlePlatform()
            val source = RawSensorDataSource(backgroundScope)
            val transport = TestGattTransport()
            val connector = GattBleConnection(transport, source, platform, backgroundScope)
            val repository = DefaultBleRepository(platform, connector, backgroundScope)
            val diagnostic = DiagnosticViewModel(repository, source)
            store.put("diagnostic", diagnostic)

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { diagnostic.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { diagnostic.demo.collect {} }

            val fixture = BleDevice("fixture-esp32", "AsistenteVial-IMU")
            connector.select(fixture)
            connector.prepare()
            runCurrent()
            transport.latest.listener.connected(); runCurrent()
            transport.latest.listener.services(validProfile); runCurrent()
            transport.latest.listener.subscribed(); runCurrent()
            transport.latest.listener.frame(rawFrame(az = 16384)); runCurrent()

            diagnostic.startDemoTest()
            runCurrent()
            assertTrue(diagnostic.demo.value.active)

            // Disconnect BLE
            transport.latest.listener.disconnected()
            runCurrent()

            // Active test cancelled immediately
            assertFalse("Demo test should be cancelled when BLE disconnects", diagnostic.demo.value.active)
            assertFalse(diagnostic.demo.value.alertVisible)
            assertFalse(diagnostic.demo.value.buzzerPending)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
