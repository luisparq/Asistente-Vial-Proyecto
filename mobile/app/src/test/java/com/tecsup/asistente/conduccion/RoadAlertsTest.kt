package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.feature.map.*
import org.junit.Assert.*
import org.junit.Test

class RoadAlertsTest {
    private val demoRoadRoute = (0..24).map { RoadPoint(-16.409 + it * 0.00018, -71.537) }

    private fun demoRoadCatalog() = RoadCatalog(
        zones = listOf(RoadZone("school-demo", "Colegio cerca", demoRoadRoute[11], 65)),
        speedSections = listOf(SpeedSection("speed-demo", demoRoadRoute[2], demoRoadRoute[24], 40)),
        traffic = listOf(RoadTraffic("traffic-demo", demoRoadRoute[24], 0, 1_800_000_000)),
    )

    @Test
    fun demoRouteActivatesAllThreeAlertTypes() {
        val catalog = demoRoadCatalog()
        val ids = demoRoadRoute.zipWithNext().flatMap { (previous, current) ->
            catalog.alerts(current, previous).keys
        }.toSet()

        assertEquals(setOf("speed:40", "zone:school-demo", "traffic:traffic-demo"), ids)
    }

    @Test
    fun trafficRequiresApproachInReportedDirectionAndAnUnexpiredReport() {
        val catalog = demoRoadCatalog()
        val position = demoRoadRoute[10]
        assertTrue("traffic:traffic-demo" in catalog.alerts(position, demoRoadRoute[9]))
        assertFalse("traffic:traffic-demo" in catalog.alerts(position, demoRoadRoute[11]))
        assertFalse("traffic:traffic-demo" in catalog.alerts(position, null))
        assertFalse("traffic:traffic-demo" in
            catalog.activeAt(catalog.traffic.single().expiresAt).alerts(position, demoRoadRoute[9]))
    }

    @Test
    fun speedLimitIsUnavailableOutsideTheSegmentAndWhenNearbyLimitsConflict() {
        val catalog = demoRoadCatalog()
        val section = catalog.speedSections.single()
        assertFalse("speed:40" in catalog.alerts(demoRoadRoute.first(), null))
        val conflicting = catalog.copy(speedSections = listOf(section, section.copy(id = "other", kmh = 30)))
        assertFalse(conflicting.alerts(demoRoadRoute[12], null).keys.any { it.startsWith("speed:") })
    }
}
