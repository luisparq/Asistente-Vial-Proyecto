package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.data.ble.DefaultBleRepository
import com.tecsup.asistente.conduccion.data.ble.PendingGattConnection
import com.tecsup.asistente.conduccion.domain.ble.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleRepositoryTest {
    @Test fun unavailableEnvironmentsBlockScanning() = runTest {
        val platform = TestBlePlatform()
        val repository = DefaultBleRepository(platform, PendingGattConnection(), backgroundScope)
        for (availability in listOf(BleAvailability.UNSUPPORTED, BleAvailability.PERMISSION_REQUIRED,
            BleAvailability.BLUETOOTH_OFF, BleAvailability.LOCATION_OFF, BleAvailability.UNAVAILABLE)) {
            platform.available.value = availability
            repository.startScan()
            runCurrent()
            assertEquals(ScanStatus.BLOCKED, repository.scan.value.status)
        }
        assertTrue(platform.sessions.isEmpty())
    }

    @Test fun duplicateAdvertisementsUpdateOneDeviceAndScanTimesOut() = runTest {
        val platform = TestBlePlatform()
        val repository = DefaultBleRepository(platform, PendingGattConnection(), backgroundScope)
        repository.startScan()
        repository.startScan()
        runCurrent()
        assertEquals(1, platform.sessions.size)
        platform.sessions[0].emit(BleDevice("test-address-1", "Fixture inicial"))
        platform.sessions[0].emit(BleDevice("test-address-1", "Fixture actualizado"))
        platform.sessions[0].emit(BleDevice("test-address-2", null))
        runCurrent()
        assertEquals(2, repository.scan.value.devices.size)
        assertEquals("Fixture actualizado", repository.scan.value.devices.first().name)
        advanceTimeBy(DefaultBleRepository.SCAN_DURATION_MILLIS)
        runCurrent()
        assertEquals(ScanStatus.TIMED_OUT, repository.scan.value.status)
        assertTrue(platform.sessions[0].stopped)
    }

    @Test fun stopAndRestartIgnoreLateCallbacksFromPreviousScan() = runTest {
        val platform = TestBlePlatform()
        val repository = DefaultBleRepository(platform, PendingGattConnection(), backgroundScope)
        repository.startScan()
        runCurrent()
        repository.stopScan()
        runCurrent()
        val old = platform.sessions[0]
        assertTrue(old.stopped)
        old.emit(BleDevice("old-test-address", "Tardío"))
        runCurrent()
        assertTrue(repository.scan.value.devices.isEmpty())
        repository.startScan()
        runCurrent()
        old.fail(ScanFailure(ScanFailureReason.PLATFORM, 3))
        old.emit(BleDevice("old-test-address", "Tardío"))
        platform.sessions[1].emit(BleDevice("new-test-address", null))
        runCurrent()
        assertEquals(ScanStatus.SCANNING, repository.scan.value.status)
        assertEquals(listOf("new-test-address"), repository.scan.value.devices.map { it.address })
    }

    @Test fun permissionRevocationOrBluetoothOffStopsWithoutAutoRestart() = runTest {
        val platform = TestBlePlatform()
        val repository = DefaultBleRepository(platform, PendingGattConnection(), backgroundScope)
        for (unavailable in listOf(BleAvailability.PERMISSION_REQUIRED, BleAvailability.BLUETOOTH_OFF,
            BleAvailability.LOCATION_OFF)) {
            platform.available.value = BleAvailability.READY
            repository.startScan()
            runCurrent()
            val session = platform.sessions.last()
            platform.available.value = unavailable
            runCurrent()
            assertTrue(session.stopped)
            assertEquals(ScanStatus.BLOCKED, repository.scan.value.status)
            platform.available.value = BleAvailability.READY
            runCurrent()
            assertEquals(ScanStatus.BLOCKED, repository.scan.value.status)
        }
    }

    @Test fun platformFailureStopsAndPreservesItsErrorCode() = runTest {
        val platform = TestBlePlatform()
        val repository = DefaultBleRepository(platform, PendingGattConnection(), backgroundScope)
        repository.startScan()
        runCurrent()
        platform.sessions[0].fail(ScanFailure(ScanFailureReason.PLATFORM, 2))
        runCurrent()
        assertEquals(ScanStatus.FAILED, repository.scan.value.status)
        assertEquals(2, repository.scan.value.failure?.platformCode)
        assertTrue(platform.sessions[0].stopped)
    }

    @Test fun synchronousFailureDoesNotLeakSessionOrTimeout() = runTest {
        val platform = TestBlePlatform().apply { failImmediately = true }
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val repository = DefaultBleRepository(platform, PendingGattConnection(), scope)
        repository.startScan()
        runCurrent()
        assertEquals(ScanStatus.FAILED, repository.scan.value.status)
        assertEquals(BleAvailability.PERMISSION_REQUIRED, repository.scan.value.availability)
        assertTrue(platform.sessions.single().stopped)
        advanceTimeBy(DefaultBleRepository.SCAN_DURATION_MILLIS)
        runCurrent()
        assertEquals(ScanStatus.FAILED, repository.scan.value.status)
    }

    @Test fun selectionOnlyAcceptsDiscoveredDevicesAndPreparationNeverConnects() = runTest {
        val platform = TestBlePlatform()
        val repository = DefaultBleRepository(platform, PendingGattConnection(), backgroundScope)
        repository.selectDevice("not-discovered")
        repository.prepareConnection()
        runCurrent()
        assertEquals(ConnectionStatus.NOT_SELECTED, repository.connection.value.status)
        repository.startScan()
        runCurrent()
        val device = BleDevice("fixture-address", "Dispositivo de prueba unitaria")
        platform.sessions[0].emit(device)
        runCurrent()
        repository.selectDevice(device.address)
        runCurrent()
        assertTrue(platform.sessions[0].stopped)
        assertEquals(ConnectionStatus.SELECTED, repository.connection.value.status)
        repository.startScan()
        runCurrent()
        repository.prepareConnection()
        runCurrent()
        assertTrue(platform.sessions.last().stopped)
        assertEquals(ConnectionStatus.AWAITING_GATT_PROFILE, repository.connection.value.status)
        assertEquals(device, repository.connection.value.device)
        repository.clearSelection()
        runCurrent()
        assertEquals(ConnectionStatus.NOT_SELECTED, repository.connection.value.status)
    }
}

/** Dobles exclusivos de pruebas; no se incluyen en el APK de la aplicación. */
internal class TestBlePlatform : BlePlatform {
    val available = MutableStateFlow(BleAvailability.READY)
    val sessions = mutableListOf<TestSession>()
    var failImmediately = false
    override fun availability() = available.value
    override fun observeAvailability() = available
    override fun startScan(onDevice: (BleDevice) -> Unit, onFailure: (ScanFailure) -> Unit): ScanSession {
        val session = TestSession(onDevice, onFailure)
        sessions += session
        if (failImmediately) onFailure(ScanFailure(ScanFailureReason.PERMISSION))
        return session
    }
}

internal class TestSession(val emit: (BleDevice) -> Unit, val fail: (ScanFailure) -> Unit) : ScanSession {
    var stopped = false
    override fun stop() { stopped = true }
}
