package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.data.sync.RetryPolicy
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ContractSyncTest {
    @Test fun utcMillisNullAndEnumsAreSerializedExactly() {
        assertEquals("1970-01-01T00:00:00.000Z", Contract.timestamp(0))
        assertEquals("1970-01-01T00:00:00.123Z", Contract.timestamp(123))
        assertEquals("{\"started_at\":\"1970-01-01T00:00:00.000Z\",\"ended_at\":null,\"status\":\"ACTIVE\",\"client_revision\":1}", Contract.json.encodeToString(TripDto(Contract.timestamp(0), null, "ACTIVE", 1)))
        val event = EventDto("SHARP_TURN", Contract.timestamp(123))
        assertEquals("SIMULATED", Contract.json.decodeFromString<EventDto>(Contract.json.encodeToString(event)).origin)
    }
    @Test fun retryClassificationsAreNotDiscarded() {
        listOf(0, 408, 429, 500, 503).forEach { assertEquals("RETRY_WAIT", RetryPolicy.state(it)) }
        listOf(401, 403).forEach { assertEquals("BLOCKED_AUTH", RetryPolicy.state(it)) }
        listOf(404, 409).forEach { assertEquals("BLOCKED_CONFLICT", RetryPolicy.state(it)) }
        assertEquals("BLOCKED_VALIDATION", RetryPolicy.state(422))
    }
    @Test fun retryAfterSecondsAndHttpDateAreRespected() {
        assertTrue(RetryPolicy.next(0, 0, "120") >= 120_000)
        assertTrue(RetryPolicy.next(0, 0, "Thu, 01 Jan 1970 00:03:00 GMT") >= 180_000)
        assertTrue(RetryPolicy.next(0, 1, "bad") in 60_000..75_000)
    }
    @Test fun localHttpIsDebugOnlyAndRedirectCredentialsAreNotAccepted() {
        val api = LaravelApi()
        assertEquals("http://10.0.2.2:8000/", api.normalizeBase("http://10.0.2.2:8000"))
        assertThrows(IllegalArgumentException::class.java) { api.normalizeBase("http://example.com/") }
        assertThrows(IllegalArgumentException::class.java) { api.normalizeBase("https://secret@example.com/") }
        assertThrows(IllegalArgumentException::class.java) { api.normalizeBase("https://example.com/?token=secret") }
    }
}
