package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.feature.diagnostic.DiagnosticViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic TEST streams and virtual time; never a real BLE or physical calibration test. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationFlowTest {
    private class TestSource : SensorDataSource {
            override val status = MutableStateFlow(SensorDataStatus.RECEIVING)
            override val sample = MutableStateFlow<RawImuSample?>(RawImuSample(0, 0, 0, 0, 0, 0))
            override val frames = MutableSharedFlow<ByteArray>()
        }
    private class TestRepository : BleRepository {
            override val scan = MutableStateFlow(BleScanState())
            override val connection = MutableStateFlow(BleConnectionState(ConnectionStatus.CONNECTED, BleDevice("TEST", "TEST IMU")))
            override fun refresh() {}
            override fun startScan() {}
            override fun stopScan() {}
            override fun selectDevice(address: String) {}
            override fun prepareConnection() {}
            override fun clearSelection() {}
        }
    private class Fixture {
        val source = TestSource()
        val ble = TestRepository()
        val model = DiagnosticViewModel(ble, source)
        val models = ViewModelStore().apply { put("TEST", model) }
    }

    private fun flowTest(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val fixture = Fixture()
        try { block(fixture) } finally { fixture.models.clear(); Dispatchers.resetMain() }
    }

    @Test fun fixedThirtySecondWindowCountsOnlyNewFramesAndIgnoresRepeatedStarts() = flowTest { f ->
        repeat(10) { f.model.startMeasurement() }
        assertTrue(f.model.calibration.value.measuring)
        assertEquals(0, f.model.calibration.value.validSamples) // Cached sample is excluded.
        f.source.frames.emit(ByteArray(12))
        f.source.frames.emit(ByteArray(11)) // Invalid length excluded.
        f.source.frames.emit(ByteArray(12).apply { this[6] = 1 })
        advanceTimeBy(29_999); runCurrent()
        assertTrue(f.model.calibration.value.measuring)
        assertNull(f.model.calibration.value.result)
        advanceTimeBy(1); runCurrent()
        assertFalse(f.model.calibration.value.measuring)
        assertEquals(2, f.model.calibration.value.result!!.validSamples)
        f.source.frames.emit(ByteArray(12)) // Finished collector no longer listens.
        assertEquals(2, f.model.calibration.value.result!!.validSamples)
    }

    @Test fun cancelStopsCollectionAndAllowsCleanRestart() = flowTest { f ->
        f.model.startMeasurement()
        f.source.frames.emit(ByteArray(12))
        f.model.cancelMeasurement()
        advanceTimeBy(30_000); runCurrent()
        assertNull(f.model.calibration.value.result)
        assertEquals(0, f.model.calibration.value.validSamples)
        f.model.startMeasurement()
        assertEquals(0, f.model.calibration.value.validSamples)
        f.source.frames.emit(ByteArray(12))
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, f.model.calibration.value.result!!.validSamples)
    }

    @Test fun disconnectDiscardsRunningAndCompletedResultsWithoutUiSubscription() = flowTest { f ->
        f.model.startMeasurement()
        f.source.frames.emit(ByteArray(12))
        f.ble.connection.value = BleConnectionState(ConnectionStatus.DISCONNECTED)
        runCurrent()
        assertFalse(f.model.calibration.value.measuring)
        assertNull(f.model.calibration.value.result)
        advanceTimeBy(30_000); runCurrent()
        assertNull(f.model.calibration.value.result)
        f.ble.connection.value = BleConnectionState(ConnectionStatus.CONNECTED, BleDevice("TEST", "TEST IMU"))
        f.model.startMeasurement()
        f.source.frames.emit(ByteArray(12))
        advanceTimeBy(30_000); runCurrent()
        assertNotNull(f.model.calibration.value.result)
        f.ble.connection.value = BleConnectionState(ConnectionStatus.DISCONNECTED)
        runCurrent()
        assertNull(f.model.calibration.value.result)
    }

    @Test fun missingConnectionOrFreshSamplePreventsStartAndInterruptedReceptionCancels() = flowTest { f ->
        f.source.status.value = SensorDataStatus.WAITING_FOR_DATA
        f.model.startMeasurement()
        assertFalse(f.model.calibration.value.measuring)
        f.source.status.value = SensorDataStatus.RECEIVING
        f.model.startMeasurement()
        f.source.frames.emit(ByteArray(12))
        f.source.status.value = SensorDataStatus.FAILED
        runCurrent()
        assertFalse(f.model.calibration.value.measuring)
        assertNull(f.model.calibration.value.result)
        advanceTimeBy(30_000); runCurrent()
        assertNull(f.model.calibration.value.result)
    }

    @Test fun noFramesNeverInventsOffsetsAndLeavingScreenCancelsRunningMeasurement() = flowTest { f ->
        f.model.startMeasurement()
        advanceTimeBy(30_000); runCurrent()
        assertNull(f.model.calibration.value.result)
        assertTrue(f.model.calibration.value.message!!.contains("No se recibieron"))
        f.model.startMeasurement()
        f.source.frames.emit(ByteArray(12))
        f.model.cancelIfMeasuring()
        advanceTimeBy(30_000); runCurrent()
        assertNull(f.model.calibration.value.result)
    }
}
