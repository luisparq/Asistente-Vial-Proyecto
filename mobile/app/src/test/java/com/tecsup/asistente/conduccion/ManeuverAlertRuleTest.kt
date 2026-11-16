package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.domain.processing.IdentifiedManeuver
import com.tecsup.asistente.conduccion.domain.processing.ManeuverAlertRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManeuverAlertRuleTest {
    private val rule = ManeuverAlertRule()

    @Test
    fun onlyTheFourthDistinctEventTriggersAndTheWindowIncludesItsBoundary() {
        val events = listOf(
            IdentifiedManeuver("turn-1", 0),
            IdentifiedManeuver("brake-1", 40_000),
            IdentifiedManeuver("acceleration-1", 80_000),
            IdentifiedManeuver("turn-2", 120_000),
        )
        events.take(3).forEach { assertTrue(rule.triggeringIds(events, it, null).isEmpty()) }
        assertEquals(events.map { it.eventId }, rule.triggeringIds(events, events.last(), null))
        val expired = events.last().copy(occurredAt = 120_001)
        assertTrue(rule.triggeringIds(events.dropLast(1) + expired, expired, null).isEmpty())
    }

    @Test
    fun replaysAndNearbyReadingsDoNotIncreaseTheCount() {
        val turn = IdentifiedManeuver("turn", 0)
        val brake = IdentifiedManeuver("brake", 5_000)
        val acceleration = IdentifiedManeuver("acceleration", 10_000)
        val events = listOf(turn, turn, turn.copy(occurredAt = 3_000),
            turn.copy(eventId = "another-reading", occurredAt = 350), brake, acceleration)
        assertTrue(rule.triggeringIds(events, acceleration, null).isEmpty())
        val next = IdentifiedManeuver("next", 20_000)
        assertEquals(listOf("turn", "brake", "acceleration", "next"),
            rule.triggeringIds(events + next, next, null))
    }

    @Test
    fun cooldownDiscardsPreviousEvidenceAndTheRuleCanBeAdjusted() {
        val configured = ManeuverAlertRule(threshold = 2, windowMillis = 1_000,
            groupingMillis = 100, cooldownMillis = 500)
        val events = listOf(
            IdentifiedManeuver("previous", 0),
            IdentifiedManeuver("during-wait", 499),
            IdentifiedManeuver("after-wait", 500),
            IdentifiedManeuver("too-close", 599),
            IdentifiedManeuver("next", 600),
        )
        events.dropLast(1).forEach { assertTrue(configured.triggeringIds(events, it, 0).isEmpty()) }
        assertEquals(listOf("after-wait", "next"), configured.triggeringIds(events, events.last(), 0))
    }
}
