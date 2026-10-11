package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.model.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.data.network.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class EventLocationTest {
    // Synthetic fixtures only; no physical observations.
    private val gps = GpsTelemetry(GpsStatus.VALID, -16.0, -71.0, 43200, 500, 7, 1000, 100)
    @Test fun validGpsUsesCombinedAgeWithoutReplacingEventTimeOrInventingGpsDate() {
        val location = EventLocationPolicy.capture(gps,1500,600)!!
        assertEquals(1000L,location.ageMillis)
        assertEquals(1000L,location.receivedAtMillis)
        assertEquals(43200,location.utcSeconds)
        assertNotNull(EventLocationPolicy.capture(gps.copy(ageMillis=1500),1500,600))
    }
    @Test fun missingStaleOrTemporallyUnrelatedGpsIsNeverAttached() {
        assertNull(EventLocationPolicy.capture(null,1500,600))
        for (status in GpsStatus.entries.filter { it != GpsStatus.VALID }) {
            assertNull(EventLocationPolicy.capture(gps.copy(status=status),1500,600))
        }
        assertNull(EventLocationPolicy.capture(gps.copy(receivedAtMillis=null),1500,600))
        assertNull(EventLocationPolicy.capture(gps,999,600))
        assertNull(EventLocationPolicy.capture(gps,1500,99))
        assertNull(EventLocationPolicy.capture(gps,2501,1601))
        assertNull(EventLocationPolicy.capture(gps.copy(ageMillis=2001),1000,100))
        assertNull(EventLocationPolicy.capture(gps.copy(latitude=Double.NaN),1500,600))
    }
    @Test fun newPayloadRoundTripsAndLegacyPayloadsRemainCompatible() {
        val dto=EventDto("SHARP_TURN",Contract.timestamp(1500),"SIMULATED",-16.0,-71.0,
            "GPS_NEO6M",43200,Contract.timestamp(1000),1000)
        assertEquals(dto,Contract.json.decodeFromString<EventDto>(Contract.json.encodeToString(dto)))
        val old=Contract.json.decodeFromString<EventDto>("""{"type":"SHARP_TURN","occurred_at":"2026-10-09T12:00:00.000Z","origin":"SIMULATED"}""")
        assertNull(old.latitude); assertNull(old.gps_received_at)
        val receipt=Contract.json.decodeFromString<EventReceipt>("""{"client_event_id":"TEST","client_trip_id":"TEST","type":"SHARP_TURN","occurred_at":"2026-10-09T12:00:00.000Z","origin":"SIMULATED"}""")
        assertNull(receipt.location_origin)
    }
}
