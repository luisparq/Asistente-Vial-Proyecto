package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.data.ble.*
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal val validProfile = GattProfileDescription(true, true, true, true)
internal fun testConnection(platform: BlePlatform, scope: CoroutineScope) =
    GattBleConnection(TestGattTransport(), RawSensorDataSource(scope), platform, scope)

/** TEST transport; no radio connection is represented as physically verified. */
internal class TestGattTransport : GattTransport {
    val sessions = mutableListOf<TestGattSession>()
    val latest get() = sessions.last()
    override fun connect(address: String, listener: GattTransport.Listener): GattTransport.Session =
        TestGattSession(listener).also { sessions += it }
}
internal class TestGattSession(val listener: GattTransport.Listener) : GattTransport.Session {
    var discovers = 0
    var subscribes = 0
    var closes = 0
    var allowSubscribe = true
    override fun discover(): Boolean { discovers++; return true }
    override fun subscribe(): Boolean { subscribes++; return allowSubscribe }
    override fun close() { closes++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GattConnectionTest {
    private val device = BleDevice("TEST-address", "TEST-only IMU")

    @Test fun connectsOnlyAfterCccdAndIgnoresRepeatedConnectAndLateCallbacks() = runTest {
        val transport = TestGattTransport()
        val source = RawSensorDataSource(backgroundScope)
        val connection = GattBleConnection(transport, source, TestBlePlatform(), backgroundScope)
        connection.select(device); connection.prepare(); connection.prepare(); runCurrent()
        assertEquals(1, transport.sessions.size)
        assertEquals(ConnectionStatus.CONNECTING, connection.state.value.status)
        val old = transport.latest
        old.listener.connected(); runCurrent()
        assertEquals(1, old.discovers)
        old.listener.services(validProfile); runCurrent()
        assertEquals(1, old.subscribes)
        assertEquals(ConnectionStatus.CONNECTING, connection.state.value.status)
        old.listener.frame(ByteArray(12)); runCurrent()
        assertNull(source.sample.value)
        old.listener.subscribed(); runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, connection.state.value.status)
        old.listener.frame(ByteArray(12)); runCurrent()
        assertEquals(SensorDataStatus.RECEIVING, source.status.value)
        old.listener.disconnected(); runCurrent()
        assertEquals(ConnectionStatus.DISCONNECTED, connection.state.value.status)
        assertEquals(1, old.closes)
        assertNull(source.sample.value)
        connection.prepare(); runCurrent()
        old.listener.frame(ByteArray(12)); old.listener.subscribed(); old.listener.failed("late TEST"); runCurrent()
        assertEquals(ConnectionStatus.CONNECTING, connection.state.value.status)
        assertNull(source.sample.value)
        connection.clear(); runCurrent()
        assertEquals(1, transport.latest.closes)
        assertEquals(ConnectionStatus.NOT_SELECTED, connection.state.value.status)
    }

    @Test fun missingProfileComponentsFailBeforeSubscription() = runTest {
        for (profile in listOf(validProfile.copy(service = false), validProfile.copy(characteristic = false),
                validProfile.copy(notify = false), validProfile.copy(cccd = false))) {
            val transport = TestGattTransport()
            val source = RawSensorDataSource(backgroundScope)
            val connection = GattBleConnection(transport, source, TestBlePlatform(), backgroundScope)
            connection.select(device); connection.prepare(); runCurrent()
            transport.latest.listener.connected(); runCurrent()
            transport.latest.listener.services(profile); runCurrent()
            assertEquals(ConnectionStatus.FAILED, connection.state.value.status)
            assertEquals(profile.error(), connection.state.value.error)
            assertEquals(0, transport.latest.subscribes)
            assertEquals(1, transport.latest.closes)
            assertEquals(SensorDataStatus.FAILED, source.status.value)
        }
    }

    @Test fun subscriptionRejectedTimeoutAndBluetoothLossReleaseGatt() = runTest {
        val transport = TestGattTransport()
        val platform = TestBlePlatform()
        val source = RawSensorDataSource(backgroundScope)
        val connection = GattBleConnection(transport, source, platform, backgroundScope)
        connection.select(device); connection.prepare(); runCurrent()
        transport.latest.allowSubscribe = false
        transport.latest.listener.connected(); runCurrent()
        transport.latest.listener.services(validProfile); runCurrent()
        assertEquals(ConnectionStatus.FAILED, connection.state.value.status)
        assertEquals(1, transport.latest.closes)
        connection.prepare(); runCurrent()
        advanceTimeBy(GattBleConnection.CONNECT_TIMEOUT_MILLIS); runCurrent()
        assertEquals(ConnectionStatus.FAILED, connection.state.value.status)
        assertEquals(1, transport.latest.closes)
        connection.prepare(); runCurrent()
        transport.latest.listener.connected(); runCurrent()
        transport.latest.listener.services(validProfile); runCurrent()
        transport.latest.listener.subscribed(); runCurrent()
        platform.available.value = BleAvailability.BLUETOOTH_OFF; runCurrent()
        assertEquals(ConnectionStatus.FAILED, connection.state.value.status)
        assertEquals(1, transport.latest.closes)
        platform.available.value = BleAvailability.READY; runCurrent()
        assertEquals(3, transport.sessions.size) // no automatic reconnection
    }

    @Test fun asynchronousCccdFailureAndPermissionRevocationNeverExposeConnected() = runTest {
        val transport = TestGattTransport()
        val platform = TestBlePlatform()
        val source = RawSensorDataSource(backgroundScope)
        val connection = GattBleConnection(transport, source, platform, backgroundScope)
        connection.select(device); connection.prepare(); runCurrent()
        transport.latest.listener.connected(); runCurrent()
        transport.latest.listener.services(validProfile); runCurrent()
        transport.latest.listener.failed("TEST CCCD rejected"); runCurrent()
        assertEquals(ConnectionStatus.FAILED, connection.state.value.status)
        connection.prepare(); runCurrent()
        platform.available.value = BleAvailability.PERMISSION_REQUIRED; runCurrent()
        assertEquals(ConnectionStatus.FAILED, connection.state.value.status)
        assertNull(source.sample.value)
        assertTrue(transport.sessions.all { it.closes == 1 })
    }
}
