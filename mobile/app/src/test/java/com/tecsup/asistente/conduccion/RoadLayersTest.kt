package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore
import com.tecsup.asistente.conduccion.domain.sync.Session
import com.tecsup.asistente.conduccion.feature.map.*
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class RoadLayersTest {
    @Test fun tilesOnlyAuthorizeTheExactServerAndEndpoint() {
        val base = "https://fleet.example.test/sub/"
        assertTrue(isDriverTileUrl(base, "${base}api/v1/traffic-flow/12/123/456.png?refresh=1".toHttpUrl()))
        listOf(
            "https://other.example.test/sub/api/v1/traffic-flow/12/123/456.png",
            "http://fleet.example.test/sub/api/v1/traffic-flow/12/123/456.png",
            "https://fleet.example.test:8443/sub/api/v1/traffic-flow/12/123/456.png",
            "https://fleet.example.test/api/v1/traffic-flow/12/123/456.png",
            "https://fleet.example.test/sub/api/v1/road/schools",
            "https://fleet.example.test/sub/api/v1/traffic-flow/12/123/456.png/extra",
        ).forEach { assertFalse(it, isDriverTileUrl(base, it.toHttpUrl())) }
    }

    @Test fun tileTokenStaysInHeaderAndRedirectsAreNotFollowed() {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val base = server.url("/").newBuilder().host("127.0.0.1").build().toString()
            val store = MemorySessionStore()
            store.save(Session(base, 1, 2, "TEST", "test@example.test", "TEST_ONLY_TOKEN"))
            val tiles = RoadTileClient(store, LaravelApi())
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "${base}other"))
            tiles.client.newCall(Request.Builder().url("${base}api/v1/traffic-flow/12/123/456.png").build()).execute().use {
                assertEquals(302, it.code)
            }
            val authenticated = server.takeRequest()
            assertEquals("Bearer TEST_ONLY_TOKEN", authenticated.getHeader("Authorization"))
            assertFalse(authenticated.path!!.contains("TOKEN"))
            assertEquals(1, server.requestCount)

            server.enqueue(MockResponse().setBody("style"))
            tiles.client.newCall(Request.Builder().url("${base}style.json").build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
            store.save(null)
            server.enqueue(MockResponse().setResponseCode(401))
            tiles.client.newCall(Request.Builder().url("${base}api/v1/traffic-flow/12/123/456.png").build()).execute().close()
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertNull(tiles.template(null, 1))
        }
    }

    @Test fun poiCoordinatesAreValidatedAndProviderTextRemainsPlainText() {
        val (features, count) = roadFeatures(RoadLayer.SCHOOLS, """{"schools":[
            {"name":"Colegio <prueba>","lat":-16.4,"lon":-71.5},
            {"name":"Invalid","lat":100,"lon":-71.5},
            {"name":"Missing"}
        ]}""")
        assertEquals(1, count)
        val feature = Json.parseToJsonElement(features).jsonObject["features"]!!.jsonArray.single().jsonObject
        assertEquals("Point", feature["geometry"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(feature["properties"]!!.jsonObject["road_label"]!!.jsonPrimitive.content.contains("Colegio <prueba>"))
    }

    @Test fun incidentLinesAndPointsAreKeptAndMalformedGeometryIsDropped() {
        val (_, count) = roadFeatures(RoadLayer.INCIDENTS, """{"incidents":[
            {"category":"Cierre","geometry":{"type":"LineString","coordinates":[[-71.5,-16.4],[-71.51,-16.41]]}},
            {"category":"Obras","geometry":{"type":"Point","coordinates":[-71.5,-16.4]}},
            {"geometry":{"type":"LineString","coordinates":[[999,-16.4],[-71.5,-16.4]]}}
        ]}""")
        assertEquals(2, count)
        assertTrue(roadServiceMessage(401).contains("sesión"))
        assertTrue(roadServiceMessage(429).contains("Límite"))
        assertTrue(roadServiceMessage(503).contains("configurado"))
    }
}
