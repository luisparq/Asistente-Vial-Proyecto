package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.network.DriverProfileDto
import com.tecsup.asistente.conduccion.data.network.Envelope
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.data.repository.LaravelDriverProfileRepository
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore
import com.tecsup.asistente.conduccion.domain.sync.Session
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DriverProfileTest {
    private fun session(server: MockWebServer) = Session(server.url("/").toString(), 1, 2, "TEST", "test@example.test", "TEST_ONLY_TOKEN")
    private val emptyProfile = """{"data":{"name":"TEST Driver","email":"test@example.test","status":"ACTIVE","access_status":"ACTIVE","emergency_contact":null,"documents":[],"vehicle":null}}"""

    @Test fun contractMapsDateOnlyWithoutTimezoneAndMinimizesFields() {
        val json = """{"data":{"name":"TEST","email":"test@example.test","status":"ACTIVE","access_status":"ACTIVE","emergency_contact":{"name":"TEST Contact","relationship":"TEST","phone":"TEST"},"documents":[{"id":1,"type":"DRIVING_LICENSE","type_name":"Licencia de conducir","review_status":"PENDING","expires_on":"2026-09-30","expired":false}],"vehicle":{"plate":"TEST","brand":"TEST","model":"TEST","status":"ACTIVE"}}}"""
        val profile = Contract.json.decodeFromString<Envelope<DriverProfileDto>>(json).data.toDomain()
        assertEquals(LocalDate.of(2026, 9, 30), profile.documents.single().expiresOn)
        assertEquals("TEST Contact", profile.emergencyContact?.name)
        assertEquals("TEST", profile.vehicle?.plate)
    }
    @Test fun getsOwnProfileUsingBearerAndHandlesEmptyFields() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(emptyProfile))
            val store = MemorySessionStore().apply { save(session(server)) }
            val repository = LaravelDriverProfileRepository(LaravelApi(), store)
            val result = withTimeout(5000) { repository.observe().first { it != ProfileState.Loading } }
            assertTrue(result is ProfileState.Ready)
            val profile = (result as ProfileState.Ready).profile
            assertTrue(profile.documents.isEmpty()); assertNull(profile.vehicle); assertNull(profile.emergencyContact)
            val request = server.takeRequest()
            assertEquals("GET", request.method); assertEquals("/api/v1/me/driver", request.path)
            assertEquals("Bearer TEST_ONLY_TOKEN", request.getHeader("Authorization"))
        }
    }
    @Test fun handlesHttpErrorsWithoutReturningPriorPersonalData() = runBlocking {
        MockWebServer().use { server ->
            val store = MemorySessionStore().apply { save(session(server)) }
            val repository = LaravelDriverProfileRepository(LaravelApi(), store)
            for ((code, expected) in listOf(401 to ProfileState.SessionExpired, 403 to ProfileState.Forbidden, 404 to ProfileState.NotRegistered, 429 to ProfileState.ServerError, 500 to ProfileState.ServerError)) {
                server.enqueue(MockResponse().setResponseCode(code))
                assertEquals(expected, withTimeout(5000) { repository.observe().first { it != ProfileState.Loading } })
            }
        }
    }
    @Test fun malformedServerDataDoesNotCrash() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("invalid"))
            val store = MemorySessionStore().apply { save(session(server)) }
            assertEquals(ProfileState.ServerError, withTimeout(5000) { LaravelDriverProfileRepository(LaravelApi(), store).observe().first { it != ProfileState.Loading } })
        }
    }
    @Test fun logoutAndMissingTokenRequireAuthenticationWithoutNetwork() = runBlocking {
        val store = MemorySessionStore()
        val repository = LaravelDriverProfileRepository(LaravelApi(), store)
        assertEquals(ProfileState.SignedOut, repository.observe().first { it != ProfileState.Loading })
        store.save(Session("http://127.0.0.1/", 1, 2, "TEST", "test@example.test", null))
        assertEquals(ProfileState.SessionExpired, repository.observe().first { it != ProfileState.Loading })
    }
    @Test fun unavailableServerReportsOffline() = runBlocking {
        val server = MockWebServer(); server.start()
        val store = MemorySessionStore().apply { save(session(server)) }; server.shutdown()
        assertEquals(ProfileState.Offline, withTimeout(5000) { LaravelDriverProfileRepository(LaravelApi(), store).observe().first { it != ProfileState.Loading } })
    }
}
