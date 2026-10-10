package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.data.ble.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GpsTelemetryTest {
    // Synthetic TEST frames only, never evidence of physical GPS reception.
    private fun frame(state: Int = 2, age: Int = 100, flags: Int = 3, lat: Int = -160000000,
        lon: Int = -710000000, utc: Int = 43200) = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        .put(1).put(state.toByte()).put(flags.toByte()).put(7).putInt(lat).putInt(lon).putInt(utc).putInt(age).array()
    @Test fun decodesSignedCoordinatesUtcSatellitesAndAge() {
        val gps=GpsDecoder.decode(frame())
        assertEquals(GpsStatus.VALID,gps.status)
        assertEquals(-16.0,gps.latitude!!,0.0000001)
        assertEquals(-71.0,gps.longitude!!,0.0000001)
        assertEquals(43200,gps.utcSeconds)
        assertEquals(100L,gps.ageMillis)
        assertEquals(7,gps.satellites)
    }
    @Test fun noFixNoReceptionAndStaleNeverExposeCoordinatesOrUtc() {
        for (state in listOf(0,1,3)) {
            val gps=GpsDecoder.decode(frame(state=state,flags=2))
            assertNull(gps.latitude); assertNull(gps.longitude); assertNull(gps.utcSeconds)
        }
        val gps=GpsDecoder.decode(frame(age=5001))
        assertEquals(GpsStatus.STALE,gps.status)
        assertNull(gps.latitude); assertNull(gps.utcSeconds)
        assertEquals(GpsStatus.VALID,GpsDecoder.decode(frame(age=5000)).status)
        assertNull(GpsDecoder.decode(frame(state=0,age=-1,flags=0)).ageMillis)
    }
    @Test fun rejectsMalformedVersionsStatesFlagsCoordinatesUtcAndLengths() {
        val invalid=listOf(ByteArray(19),ByteArray(21),frame().apply { this[0]=2 },frame(state=9),
            frame(flags=4),frame(lat=910000000),frame(lon=1810000000),frame(utc=86400),frame(state=1),
            frame().apply { this[3]=100 })
        invalid.forEach { bytes -> assertThrows(IllegalArgumentException::class.java) { GpsDecoder.decode(bytes) } }
    }
    @Test fun agesWithoutFurtherNotificationsAndRecoversWithoutAffectingImu() = runTest {
        val source=RawSensorDataSource(backgroundScope)
        source.accept(ByteArray(12)); source.acceptGps(frame(age=4900)); runCurrent()
        advanceTimeBy(101); runCurrent()
        assertEquals(GpsStatus.STALE,source.gps.value.status)
        assertNull(source.gps.value.latitude)
        assertEquals(SensorDataStatus.RECEIVING,source.status.value)
        source.acceptGps(frame()); assertEquals(GpsStatus.VALID,source.gps.value.status)
        source.acceptGps(ByteArray(1)); assertEquals(GpsStatus.INVALID_FRAME,source.gps.value.status)
        assertEquals(SensorDataStatus.RECEIVING,source.status.value)
        source.reset(SensorDataStatus.DISCONNECTED)
        assertEquals(GpsStatus.DISCONNECTED,source.gps.value.status)
        advanceTimeBy(6000); runCurrent()
        assertEquals(GpsStatus.DISCONNECTED,source.gps.value.status)
    }
    @Test fun missingGpsAndLateGpsCallbacksDoNotBreakImuConnection() = runTest {
        val transport=TestGattTransport(); val source=RawSensorDataSource(backgroundScope)
        val connection=GattBleConnection(transport,source,TestBlePlatform(),backgroundScope)
        connection.select(BleDevice("TEST-address","TEST GPS")); connection.prepare(); runCurrent()
        val listener=transport.latest.listener
        listener.connected(); runCurrent(); listener.services(validProfile); runCurrent()
        listener.subscribed(); runCurrent(); listener.gpsUnavailable(); runCurrent()
        assertEquals(GpsStatus.UNAVAILABLE,source.gps.value.status)
        listener.frame(ByteArray(12)); runCurrent()
        assertEquals(ConnectionStatus.CONNECTED,connection.state.value.status)
        assertNotNull(source.sample.value)
        listener.gpsFrame(frame()); runCurrent()
        assertEquals(GpsStatus.VALID,source.gps.value.status)
        listener.disconnected(); runCurrent(); listener.gpsFrame(frame()); runCurrent()
        assertEquals(GpsStatus.DISCONNECTED,source.gps.value.status)
        assertNull(source.sample.value)
    }
    @Test fun missingGpsNotificationsBecomeWaitingAndNewFramesRecover() = runTest {
        val source=RawSensorDataSource(backgroundScope)
        source.acceptGps(frame(state=1,flags=2)); runCurrent()
        advanceTimeBy(5001); runCurrent()
        assertEquals(GpsStatus.WAITING,source.gps.value.status)
        assertNull(source.gps.value.satellites)
        source.acceptGps(frame(state=0,flags=0))
        assertEquals(GpsStatus.NO_RECEPTION,source.gps.value.status)
        source.reset(SensorDataStatus.DISCONNECTED)
    }
}
