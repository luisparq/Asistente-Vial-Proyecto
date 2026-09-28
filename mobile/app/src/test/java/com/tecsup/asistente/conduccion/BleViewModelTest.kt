package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.data.ble.DefaultBleRepository
import com.tecsup.asistente.conduccion.data.ble.PendingGattConnection
import com.tecsup.asistente.conduccion.data.ble.PendingSensorDataSource
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import com.tecsup.asistente.conduccion.feature.ble.BleViewModel
import com.tecsup.asistente.conduccion.feature.diagnostic.DiagnosticViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleViewModelTest {
    @Test fun viewModelsShareSelectionAndNeverExposeSensorSamples() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val platform = TestBlePlatform()
            val repository = DefaultBleRepository(platform, PendingGattConnection(), backgroundScope)
            val source = PendingSensorDataSource()
            val ble = BleViewModel(repository)
            val diagnostic = DiagnosticViewModel(repository, source)
            store.put("ble", ble)
            store.put("diagnostic", diagnostic)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ble.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { diagnostic.state.collect {} }
            ble.startScan()
            runCurrent()
            assertEquals(ScanStatus.SCANNING, ble.state.value.scan.status)
            val fixture = BleDevice("fixture-address", null)
            platform.sessions.single().emit(fixture)
            runCurrent()
            ble.select(fixture.address)
            ble.prepareConnection()
            runCurrent()
            assertEquals(ConnectionStatus.AWAITING_GATT_PROFILE, ble.state.value.connection.status)
            assertEquals(ble.state.value.connection, diagnostic.state.value.connection)
            assertEquals(SensorDataStatus.PENDING_HARDWARE, diagnostic.state.value.sensorStatus)
            assertTrue(source.frames.toList().isEmpty())
            ble.startScan()
            runCurrent()
            store.clear()
            runCurrent()
            assertTrue(platform.sessions.last().stopped)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
