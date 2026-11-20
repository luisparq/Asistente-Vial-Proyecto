package com.tecsup.asistente.conduccion

import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore
import com.tecsup.asistente.conduccion.domain.sync.Session
import com.tecsup.asistente.conduccion.feature.login.ServerConfiguration
import com.tecsup.asistente.conduccion.feature.map.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RoadLayerRepositoryTest {
    @Test fun layersUseTheDriverSessionAndStopRequestingWhenDisabledOrLoggedOut() = runBlocking {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val base = server.url("/").newBuilder().host("127.0.0.1").build().toString()
            val sessions = MemorySessionStore()
            sessions.save(Session(base, 1, 2, "TEST", "test@example.test", "TEST_ONLY_ROAD_TOKEN"))
            val api = LaravelApi()
            val configuration = ServerConfiguration(InstrumentationRegistry.getInstrumentation().targetContext, api, sessions)
            val repository = RoadLayerRepository(api, sessions, configuration)
            val selected = MutableStateFlow(setOf(RoadLayer.SCHOOLS))
            val center = MutableStateFlow(RoadPoint(-16.4, -71.5))
            server.enqueue(MockResponse().setBody("""{"success":true,"schools":[{"name":"TEST school","lat":-16.4,"lon":-71.5}]}"""))
            val result = withTimeout(5000) { repository.observe(RoadLayer.SCHOOLS, selected, center).first { it.message != "Cargando…" } }
            assertTrue(result.features.contains("TEST school"))
            val request = server.takeRequest()
            assertEquals("Bearer TEST_ONLY_ROAD_TOKEN", request.getHeader("Authorization"))
            assertEquals("/api/v1/road/schools?lat=-16.4&lon=-71.5", request.path)

            selected.value = emptySet()
            assertFalse(repository.observe(RoadLayer.SCHOOLS, selected, center).first().enabled)
            selected.value = setOf(RoadLayer.SCHOOLS)
            sessions.save(null)
            val loggedOut = repository.observe(RoadLayer.SCHOOLS, selected, center).first()
            assertEquals(EMPTY_ROAD_FEATURES, loggedOut.features)
            assertTrue(loggedOut.message.contains("Inicia sesión"))
            assertEquals(1, server.requestCount)
        }
    }
}
