package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.feature.map.Congestion
import com.tecsup.asistente.conduccion.feature.map.RoadPoint
import com.tecsup.asistente.conduccion.feature.map.SchoolProximity
import org.junit.Assert.*
import org.junit.Test

class TripRoadDataTest {
    @Test fun schoolVoiceWaitsForEntryAndDoesNotRepeatAtTheEdge() {
        val school = SchoolProximity()
        var announcements = 0
        val speak = { announcements++; true }
        school.update(66.0, speak)
        assertEquals(0, announcements)
        school.update(65.0, speak)
        school.update(80.0, speak)
        school.update(null, speak)
        school.update(63.0, speak)
        assertEquals(1, announcements)
        school.update(86.0, speak)
        school.update(64.0, speak)
        assertEquals(2, announcements)
    }

    @Test fun trafficMustBeAheadInTheSameTravelDirectionAndNearTheProjectedPath() {
        val position = RoadPoint(0.0, 0.0)
        val forward = listOf(RoadPoint(0.001, 0.0), RoadPoint(0.002, 0.0))
        assertTrue(Congestion(forward, 0).isAhead(position, 0))
        assertFalse(Congestion(forward.reversed(), 0).isAhead(position, 0))
        assertFalse(Congestion(forward, 0).isAhead(position, 180))
        assertFalse(Congestion(forward.map { it.copy(lon = 0.001) }, 0).isAhead(position, 0))
        assertFalse(Congestion(forward.map { it.copy(lat = it.lat + 0.01) }, 0).isAhead(position, 0))
    }
}
