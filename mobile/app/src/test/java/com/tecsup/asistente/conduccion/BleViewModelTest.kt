package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.data.ble.DefaultBleRepository
import com.tecsup.asistente.conduccion.data.ble.GattBleConnection
import com.tecsup.asistente.conduccion.data.ble.RawSensorDataSource
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import com.tecsup.asistente.conduccion.feature.ble.BleViewModel
import com.tecsup.asistente.conduccion.feature.diagnostic.DiagnosticViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BleViewModelTest {
    @Test fun viewModelsShareConnectionAndOnlyExposeReceivedSamples() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val platform = TestBlePlatform()
            val source = RawSensorDataSource(backgroundScope)
            val transport = TestGattTransport()
            val connector = GattBleConnection(transport, source, platform, backgroundScope)
            val repository = DefaultBleRepository(platform, connector, backgroundScope)
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
            assertEquals(ConnectionStatus.CONNECTING, ble.state.value.connection.status)
            assertEquals(ble.state.value.connection, diagnostic.state.value.connection)
            assertEquals(SensorDataStatus.WAITING_FOR_DATA, diagnostic.state.value.sensorStatus)
            assertNull(diagnostic.state.value.sample)
            transport.latest.listener.connected(); runCurrent()
            transport.latest.listener.services(validProfile); runCurrent()
            transport.latest.listener.subscribed(); runCurrent()
            transport.latest.listener.frame(byteArrayOf(1,0,2,0,3,0,4,0,5,0,6,0)); runCurrent()
            assertEquals(1, diagnostic.state.value.sample!!.ax)
            assertEquals(6, diagnostic.state.value.sample!!.gz)
            assertEquals(SensorDataStatus.RECEIVING, diagnostic.state.value.sensorStatus)
            assertNotNull(diagnostic.state.value.processing)
            ble.clearSelection()
            runCurrent()
            assertNull(diagnostic.state.value.sample)
            assertNull(diagnostic.state.value.processing)
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
