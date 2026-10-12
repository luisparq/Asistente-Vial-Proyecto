package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.data.ble.*
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.domain.sync.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

internal class TestRememberedBleStore : RememberedBleStore {
    val values = mutableMapOf<String, RememberedBleDevice>()
    override fun read(owner: String) = values[owner]
    override fun save(owner: String, device: RememberedBleDevice?) { if (device == null) values.remove(owner) else values[owner] = device }
}
@OptIn(ExperimentalCoroutinesApi::class)
class BleReconnectTest {
    private val account = Session("https://test.invalid/", 1, 1, "TEST", "test@example.invalid", "TEST")
    private val device = RememberedBleDevice("AA:BB:CC:DD:EE:01", "TEST-only IMU", true)
    private class Rig(scope: TestScope, val accounts: MemorySessionStore, val prefs: TestRememberedBleStore) {
        val platform = TestBlePlatform()
        val transport = TestGattTransport()
        val source = RawSensorDataSource(scope.backgroundScope)
        val connector = GattBleConnection(transport, source, platform, scope.backgroundScope)
        val controller = BleReconnectController(platform, connector, accounts, prefs, scope.backgroundScope)
    }
    private fun rig(scope: TestScope, enabled: Boolean = true): Rig {
        val accounts = MemorySessionStore().apply { save(account) }
        val prefs = TestRememberedBleStore().apply { save(account.ownerKey, device.copy(automatic = enabled)) }
        return Rig(scope, accounts, prefs)
    }
    private fun TestScope.complete(rig: Rig) {
        rig.transport.latest.listener.connected(); runCurrent()
        rig.transport.latest.listener.services(validProfile); runCurrent()
        rig.transport.latest.listener.subscribed(); runCurrent()
    }
    private fun gps() = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        .put(1).put(2).put(3).put(7).putInt(-160000000).putInt(-710000000).putInt(43200).putInt(100).array()

    @Test fun synchronousGattFailureCannotStartOverlappingRetryCycles() = runTest {
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val platform = TestBlePlatform()
        var opens = 0
        val transport = object : GattTransport {
            override fun connect(address: String, listener: GattTransport.Listener): GattTransport.Session {
                opens++; throw IllegalStateException("TEST immediate GATT failure")
            }
        }
        val connector = GattBleConnection(transport, RawSensorDataSource(scope), platform, scope)
        val accounts = MemorySessionStore().apply { save(account) }
        val prefs = TestRememberedBleStore().apply { save(account.ownerKey, device) }
        val controller = BleReconnectController(platform, connector, accounts, prefs, scope)
        controller.foreground(true); runCurrent(); assertEquals(1, opens)
        advanceTimeBy(5000); runCurrent(); assertEquals(2, opens)
        advanceTimeBy(15000); runCurrent(); assertEquals(3, opens)
        advanceTimeBy(30000); runCurrent(); assertEquals(4, opens)
        advanceTimeBy(300000); runCurrent(); assertEquals(4, opens)
    }

    @Test fun restoredDeviceConnectsOnlyInForegroundAndNeverStartsAnAutomaticScan() = runTest {
        val r = rig(this); runCurrent()
        assertTrue(r.transport.sessions.isEmpty())
        r.controller.foreground(true); r.controller.foreground(true); r.controller.enable(); runCurrent()
        assertEquals(1, r.transport.sessions.size)
        complete(r)
        r.controller.refresh(); r.controller.enable(); runCurrent()
        assertEquals(ConnectionStatus.CONNECTED, r.connector.state.value.status)
        assertEquals(1, r.transport.sessions.size)
        assertTrue(r.platform.sessions.isEmpty())
    }
    @Test fun lossReconnectsWithNewDiscoverySubscriptionAndBothFreshStreams() = runTest {
        val r = rig(this); r.controller.foreground(true); runCurrent(); complete(r)
        val old = r.transport.latest
        old.listener.frame(ByteArray(12)); old.listener.gpsFrame(gps()); runCurrent()
        assertNotNull(r.source.sample.value); assertEquals(GpsStatus.VALID, r.source.gps.value.status)
        old.listener.disconnected(); runCurrent()
        assertNull(r.source.sample.value); assertNull(r.source.gps.value.latitude)
        assertTrue(r.controller.state.value.running)
        advanceTimeBy(5000); runCurrent()
        assertEquals(2, r.transport.sessions.size)
        old.listener.subscribed(); old.listener.frame(ByteArray(12)); old.listener.gpsFrame(gps()); runCurrent()
        assertNull(r.source.sample.value); assertNull(r.source.gps.value.latitude)
        complete(r)
        assertEquals(1, r.transport.latest.discovers); assertEquals(1, r.transport.latest.subscribes)
        r.transport.latest.listener.frame(ByteArray(12)); r.transport.latest.listener.gpsFrame(gps()); runCurrent()
        assertNotNull(r.source.sample.value); assertEquals(GpsStatus.VALID, r.source.gps.value.status)
    }
    @Test fun repeatedFailuresStopAfterFourProgressiveAttempts() = runTest {
        val r = rig(this); r.controller.foreground(true); runCurrent()
        for (delay in listOf(5000L, 15000L, 30000L)) {
            val count = r.transport.sessions.size
            r.transport.latest.listener.failed("TEST unreachable"); runCurrent()
            advanceTimeBy(delay - 1); runCurrent(); assertEquals(count, r.transport.sessions.size)
            advanceTimeBy(1); runCurrent(); assertEquals(count + 1, r.transport.sessions.size)
        }
        r.transport.latest.listener.failed("TEST unreachable"); runCurrent()
        advanceTimeBy(300000); runCurrent()
        assertEquals(4, r.transport.sessions.size)
        assertFalse(r.controller.state.value.running)
        assertTrue(r.controller.state.value.message!!.contains("agotados"))
    }
    @Test fun manualDisconnectIsPersistentAndExplicitEnableResumes() = runTest {
        val r = rig(this); r.controller.foreground(true); runCurrent(); complete(r)
        r.controller.disconnect(); runCurrent()
        assertFalse(r.prefs.read(account.ownerKey)!!.automatic)
        advanceTimeBy(300000); runCurrent(); assertEquals(1, r.transport.sessions.size)
        val reopened = Rig(this, r.accounts, r.prefs)
        reopened.controller.foreground(true); runCurrent()
        assertTrue(reopened.transport.sessions.isEmpty())
        reopened.controller.enable(); reopened.controller.enable(); runCurrent()
        assertEquals(1, reopened.transport.sessions.size)
    }
    @Test fun permissionsBluetoothAndBackgroundSuspendAttemptsUntilRestored() = runTest {
        val r = rig(this)
        r.platform.available.value = BleAvailability.PERMISSION_REQUIRED
        r.controller.foreground(true); runCurrent(); assertTrue(r.transport.sessions.isEmpty())
        r.platform.available.value = BleAvailability.READY; runCurrent(); assertEquals(1, r.transport.sessions.size)
        r.controller.foreground(false); runCurrent(); assertEquals(1, r.transport.latest.closes)
        advanceTimeBy(300000); runCurrent(); assertEquals(1, r.transport.sessions.size)
        r.platform.available.value = BleAvailability.BLUETOOTH_OFF
        r.controller.foreground(true); runCurrent(); assertEquals(1, r.transport.sessions.size)
        r.platform.available.value = BleAvailability.READY; runCurrent(); assertEquals(2, r.transport.sessions.size)
        complete(r)
        r.platform.available.value = BleAvailability.PERMISSION_REQUIRED; runCurrent()
        assertNull(r.source.sample.value)
        r.platform.available.value = BleAvailability.READY; runCurrent(); assertEquals(3, r.transport.sessions.size)
    }
    @Test fun changingAccountNeverUsesPreviousDeviceAndDemoIsNotRemembered() = runTest {
        val r = rig(this); r.controller.foreground(true); runCurrent(); complete(r)
        r.accounts.save(account.copy(userId = 2)); runCurrent()
        assertEquals(ConnectionStatus.NOT_SELECTED, r.connector.state.value.status)
        assertEquals(1, r.transport.sessions.size)
        assertNotNull(r.prefs.read(account.ownerKey))
        r.accounts.save(null); runCurrent(); r.controller.enable(); runCurrent()
        assertEquals(1, r.transport.sessions.size)
        r.accounts.save(account); runCurrent(); assertEquals(2, r.transport.sessions.size)
    }
    @Test fun selectedReplacementRequiresExplicitConnectAndForgetCancelsAllRetries() = runTest {
        val r = rig(this); r.controller.foreground(true); runCurrent(); complete(r)
        val replacement = BleDevice("AA:BB:CC:DD:EE:02", "TEST replacement")
        r.controller.select(replacement); r.controller.connectSelected(); runCurrent()
        assertEquals(replacement, r.connector.state.value.device)
        assertEquals(2, r.transport.sessions.size)
        assertEquals(replacement.address, r.prefs.read(account.ownerKey)!!.address)
        r.controller.forget(); runCurrent(); advanceTimeBy(300000); runCurrent()
        assertNull(r.prefs.read(account.ownerKey)); assertEquals(ConnectionStatus.NOT_SELECTED, r.connector.state.value.status)
        assertEquals(2, r.transport.sessions.size)
    }
}
