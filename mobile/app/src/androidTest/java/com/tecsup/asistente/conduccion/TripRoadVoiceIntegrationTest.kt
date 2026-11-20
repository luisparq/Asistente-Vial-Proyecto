package com.tecsup.asistente.conduccion

import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry
import com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore
import com.tecsup.asistente.conduccion.domain.sync.Session
import com.tecsup.asistente.conduccion.feature.login.ServerConfiguration
import com.tecsup.asistente.conduccion.feature.map.TripRoadData
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class TripRoadVoiceIntegrationTest {
    @Test fun monitorsSchoolSpeedAndCongestionWithoutAMapScreen() = runBlocking {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = when (request.requestUrl?.encodedPath) {
                        "/api/v1/road/schools" -> """{"schools":[{"lat":-16.3992,"lon":-71.53}]}"""
                        "/api/v1/road/reverse-geocode" ->
                            """{"address":{"speedLimitKmh":40,"roadPosition":"-16.3992,-71.53"}}"""
                        "/api/v1/road/traffic-flow-segment" -> """{"flow":{
                            "currentSpeedKmh":16,"freeFlowSpeedKmh":45,"confidence":0.91,
                            "coordinates":[{"latitude":-16.398,"longitude":-71.53},{"latitude":-16.397,"longitude":-71.53}]
                        }}"""
                        else -> return MockResponse().setResponseCode(503)
                    }
                    return MockResponse().setBody(body)
                }
            }
            val base = server.url("/").newBuilder().host("127.0.0.1").build().toString()
            val session = Session(base, 1, 1, "TEST", "test@example.test", "TEST-road-token")
            val api = LaravelApi()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val roads = TripRoadData(api, ServerConfiguration(context, api, MemorySessionStore()))
            val gps = MutableStateFlow(GpsTelemetry(GpsStatus.VALID, -16.4000, -71.53))
            val messages = CopyOnWriteArrayList<String>()
            val job = launch(Dispatchers.Main) {
                roads.monitor(session, gps) { _, message -> messages.add(message) }
            }

            try {
                withTimeout(12_000) {
                    // Start outside 65 m and let the monitor receive the first fix.
                    delay(1_000)
                    var receivedAt = 1L
                    while (messages.size < 3) {
                        gps.value = GpsTelemetry(GpsStatus.VALID, -16.3992, -71.53, receivedAtMillis = receivedAt++)
                        delay(150)
                    }
                }
                assertEquals(setOf(
                    "Precaución, estás en zona escolar",
                    "Límite de velocidad en esta vía: 40 kilómetros por hora",
                    "Tráfico adelante",
                ), messages.toSet())
            } finally {
                job.cancelAndJoin()
            }
        }
    }
}
