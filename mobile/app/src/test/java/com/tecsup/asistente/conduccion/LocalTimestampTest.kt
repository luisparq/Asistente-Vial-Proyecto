package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.core.ui.formatTimestamp
import com.tecsup.asistente.conduccion.data.network.Contract
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTimestampTest {
    @Test fun manualTripAndEventDisplayPreviousDayInLimaWithoutChangingUtc() {
        val cases = listOf(
            "2026-09-30T02:26:55.999Z" to "29 Sept 2026, 21:26:55",
            "2026-09-30T02:27:11.905Z" to "29 Sept 2026, 21:27:11",
            "2026-09-30T02:27:05.016Z" to "29 Sept 2026, 21:27:05",
        )
        cases.forEach { (utc, local) ->
            val instant = Instant.parse(utc)
            assertEquals("$local (America/Lima)", formatTimestamp(instant, ZoneId.of("America/Lima"), Locale.UK))
            assertEquals(utc, Contract.timestamp(instant.toEpochMilli()))
        }
    }

    @Test fun utcDeviceDisplaysUtcRatherThanApplyingFixedPeruOffset() {
        assertEquals("30 Sept 2026, 02:26:55 (UTC)", formatTimestamp(
            Instant.parse("2026-09-30T02:26:55.999Z"), ZoneId.of("UTC"), Locale.UK))
    }

    @Test fun defaultZoneIsResolvedAgainAfterDeviceZoneChanges() {
        val previous = TimeZone.getDefault()
        val instant = Instant.parse("2026-09-30T02:26:55.999Z")
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT"))
            assertEquals("30 Sept 2026, 02:26:55 (GMT)", formatTimestamp(instant, locale = Locale.UK))
            TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"))
            assertEquals("29 Sept 2026, 21:26:55 (America/Lima)", formatTimestamp(instant, locale = Locale.UK))
        } finally { TimeZone.setDefault(previous) }
    }
}
