package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.feature.map.*
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.domain.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DriverMapTest {
    private val connected=BleConnectionState(ConnectionStatus.CONNECTED)
    // Synthetic TEST inputs; no claimed physical measurements.
    private val gps=GpsTelemetry(GpsStatus.VALID,-16.0,-71.0,43200,100)
    @Test fun currentPositionRequiresConnectionValidFixAndFreshAge() {
        assertEquals(MapPosition(-16.0,-71.0),driverMapState(connected,gps,null).position)
        assertNull(driverMapState(BleConnectionState(ConnectionStatus.DISCONNECTED),gps,null).position)
        for(status in GpsStatus.entries.filter { it!=GpsStatus.VALID }) assertNull(driverMapState(connected,gps.copy(status=status),null).position)
        assertNull(driverMapState(connected,gps.copy(ageMillis=5001),null).position)
        assertNull(driverMapState(connected,gps.copy(latitude=null),null).position)
        assertNull(driverMapState(connected,gps.copy(latitude=Double.NaN),null).position)
    }
    @Test fun onlyGeoreferencedSimulatedEventsFromActiveTripAppear() {
        val event=DrivingEvent(1,2,DrivingEventType.HARSH_BRAKING,Instant.EPOCH,DataOrigin.SIMULATED,-16.0,-71.0)
        val trip=TripWithEvents(Trip(2,Instant.EPOCH,null,TripStatus.ACTIVE),listOf(event,
            event.copy(id=3,origin=DataOrigin.SENSOR),event.copy(id=4,latitude=null),event.copy(id=5,longitude=181.0),event.copy(id=6,tripId=99)))
        val state=driverMapState(connected,gps,trip)
        assertEquals(listOf(event),state.events)
        assertEquals(2L,state.tripId)
        assertTrue(driverMapState(connected,gps,trip.copy(trip=trip.trip.copy(status=TripStatus.COMPLETED))).events.isEmpty())
        assertEquals(listOf(event),driverMapState(BleConnectionState(),gps,trip).events)
    }
    @Test fun newGpsUpdatesReplacePositionAndLossClearsIt() {
        assertEquals(-16.1,driverMapState(connected,gps.copy(latitude=-16.1),null).position!!.latitude,0.0)
        val lost=driverMapState(connected,gps.copy(status=GpsStatus.STALE),null)
        assertEquals(GpsStatus.STALE,lost.gpsStatus);assertNull(lost.position)
    }
}
