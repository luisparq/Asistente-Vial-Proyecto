package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.data.ble.RawSensorDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImuRawTest {
    @Test fun exactLittleEndianAxisOrderAndSignedExtremes() {
        // Fixed TEST vector, not a physical reading: 4660, -4660, 32767, -32768, -1, 0.
        val bytes = byteArrayOf(0x34,0x12,0xcc.toByte(),0xed.toByte(),0xff.toByte(),0x7f,
            0,0x80.toByte(),0xff.toByte(),0xff.toByte(),0,0)
        assertEquals(RawImuSample(4660,-4660,32767,-32768,-1,0), ImuRawDecoder.decode(bytes))
    }
    @Test fun rejectsAllIncorrectFrameSizesWithoutPaddingOrTruncation() {
        for (size in listOf(0,1,6,11,13,14,20,244)) {
            assertThrows(IllegalArgumentException::class.java) { ImuRawDecoder.decode(ByteArray(size)) }
        }
    }
    @Test fun samplesBecomeStaleAndErrorsNeverExposeOldReadings() = runTest {
        val source = RawSensorDataSource(backgroundScope)
        assertNull(source.sample.value)
        assertEquals(SensorDataStatus.DISCONNECTED, source.status.value)
        source.reset(SensorDataStatus.WAITING_FOR_DATA)
        val frame = ByteArray(12).apply { this[0] = 42 }
        source.accept(frame)
        frame[0] = 0
        assertEquals(42, source.sample.value!!.ax)
        runCurrent()
        advanceTimeBy(RawSensorDataSource.STALE_AFTER_MILLIS)
        runCurrent()
        assertEquals(SensorDataStatus.WAITING_FOR_DATA, source.status.value)
        assertNull(source.sample.value)
        source.accept(ByteArray(13))
        assertEquals(SensorDataStatus.FAILED, source.status.value)
        source.accept(ByteArray(12))
        assertEquals(SensorDataStatus.RECEIVING, source.status.value)
        source.reset(SensorDataStatus.DISCONNECTED)
        assertNull(source.sample.value)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(SensorDataStatus.DISCONNECTED, source.status.value)
    }
}
