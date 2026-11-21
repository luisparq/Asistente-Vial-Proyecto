package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.feature.map.Congestion
import com.tecsup.asistente.conduccion.feature.map.RoadPoint
import com.tecsup.asistente.conduccion.feature.map.SchoolProximity
import org.junit.Assert.*
import org.junit.Test

class TripRoadDataTest {
    @Test fun schoolVoiceWarnsBeforeEntryAndDoesNotRepeatAtTheEdge() {
        val school = SchoolProximity()
        var announcements = 0
        val speak = { announcements++; true }
        school.update(121.0, speak)
        assertEquals(0, announcements)
        school.update(120.0, speak)
        school.update(140.0, speak)
        school.update(null, speak)
        school.update(118.0, speak)
        assertEquals(1, announcements)
        school.update(151.0, speak)
        school.update(119.0, speak)
        assertEquals(2, announcements)
    }

    @Test fun congestionCanEnterThePathBeforeTheLookaheadPoint() {
        val position = RoadPoint(0.0, 0.0)
        val bendsAway = listOf(RoadPoint(0.00045, 0.0), RoadPoint(0.0018, 0.0006))
        assertTrue(Congestion(bendsAway, 0).isAhead(position, 0))
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
