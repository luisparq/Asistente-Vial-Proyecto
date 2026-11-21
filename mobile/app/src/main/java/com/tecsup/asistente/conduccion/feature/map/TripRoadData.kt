package com.tecsup.asistente.conduccion.feature.map

import android.os.SystemClock
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry
import com.tecsup.asistente.conduccion.domain.sync.Session
import com.tecsup.asistente.conduccion.feature.login.ServerConfiguration
import javax.inject.Inject
import kotlin.math.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class TripRoadData @Inject constructor(
    private val api: LaravelApi,
    private val configuration: ServerConfiguration,
) {
    private val json = Json { ignoreUnknownKeys = true }

    internal suspend fun monitor(
        session: Session?, gps: StateFlow<GpsTelemetry>, speak: (String, String) -> Boolean,
    ): Unit = coroutineScope {
        val schools = RoadLookup<List<RoadPoint>>(330_000)
        val posted = RoadLookup<PostedLimit>(15_000)
        val traffic = RoadLookup<Congestion>(65_000)
        val osm = RoadLookup<RoadCatalog>(360_000)
        val base = session?.baseUrl ?: configuration.baseUrl
        val token = session?.token

        var current: RoadPoint? = null
        var anchor: RoadPoint? = null
        var heading: Int? = null
        var movingAt = 0L
        val schoolZone = SchoolProximity()
        var lastLimit: Int? = null
        var lastTrafficAnnouncement: Long? = null

        if (!token.isNullOrBlank()) {
            launch {
                schools.poll({ current }, 400.0, 5_000, 300_000) { point ->
                    fetch(base, "api/v1/road/schools?lat=${point.lat}&lon=${point.lon}", token) { payload ->
                        val points = payload.jsonObject["schools"]?.jsonArray ?: return@fetch null
                        points.mapNotNull { node ->
                            node.jsonObject.point("lat", "lon")
                        }
                    }
                }
            }
            launch {
                posted.poll({ current.takeIf { heading != null } }, 25.0, 3_000, 12_000) { point ->
                    val direction = heading ?: return@poll null
                    fetch(base, "api/v1/road/reverse-geocode?lat=${point.lat}&lon=${point.lon}&heading=$direction", token) { payload ->
                        val address = payload.jsonObject["address"]?.jsonObject ?: return@fetch null
                        val limit = address["speedLimitKmh"]?.jsonPrimitive?.intOrNull ?: return@fetch null
                        val matched = address["roadPosition"]?.matchedPosition() ?: return@fetch null
                        PostedLimit(limit, matched, direction).takeIf { limit in 5..150 && matched.distanceTo(point) <= 25 }
                    }
                }
            }
            launch {
                traffic.poll({ current.takeIf { heading != null } }, 60.0, 8_000, 25_000, direction = { heading }) { point ->
                    val direction = heading ?: return@poll null
                    val ahead = point.ahead(direction, 180.0)
                    if (!ahead.valid()) return@poll null
                    fetch(base, "api/v1/road/traffic-flow-segment?lat=${ahead.lat}&lon=${ahead.lon}", token) { payload ->
                        val flow = payload.jsonObject["flow"]?.jsonObject ?: return@fetch null
                        val speed = flow["currentSpeedKmh"]?.jsonPrimitive?.doubleOrNull ?: return@fetch null
                        val free = flow["freeFlowSpeedKmh"]?.jsonPrimitive?.doubleOrNull ?: return@fetch null
                        val confidence = flow["confidence"]?.jsonPrimitive?.doubleOrNull ?: return@fetch null
                        if (confidence !in 0.5..1.0 || free !in 5.0..200.0 || speed !in 0.0..(free * 0.65)) return@fetch null
                        val line = flow["coordinates"]?.jsonArray.orEmpty().map { coordinate ->
                            coordinate.jsonObject.point("latitude", "longitude") ?: return@fetch null
                        }
                        Congestion(line, direction).takeIf { line.size >= 2 }
                    }
                }
            }
        }
        launch {
            osm.poll({ current }, Double.POSITIVE_INFINITY, 60_000, 300_000) {
                fetch(base, "api/v1/road-catalog", null) { payload ->
                    json.decodeFromJsonElement<RoadCatalog>(payload).validated()
                }
            }
        }

        gps.collect { fix ->
            val point = fix.takeIf { it.status == GpsStatus.VALID }?.let {
                RoadPoint(it.latitude ?: return@let null, it.longitude ?: return@let null).takeIf(RoadPoint::valid)
            }
            current = point
            if (point == null) {
                heading = null
                anchor = null
                return@collect
            }
            val now = SystemClock.elapsedRealtime()
            val previous = anchor
            val movement = previous?.distanceTo(point)
            if (previous == null || movement == null || movement > 150) {
                anchor = point
                heading = null
            } else if (movement >= 12) {
                val (east, north) = point.offsetFrom(previous)
                heading = (Math.toDegrees(atan2(east, north)).roundToInt() + 360) % 360
                anchor = point
                movingAt = now
            } else if (now - movingAt > 8_000) {
                heading = null
            }

            val catalog = osm.fresh(now)
            val providerSchools = schools.fresh(now, point, 1000.0)
            val catalogSchools = catalog?.takeIf { it.osmAvailable }?.zones?.map { it.point }
            if (providerSchools != null || catalogSchools != null) {
                val schoolDistance = (providerSchools.orEmpty() + catalogSchools.orEmpty())
                    .minOfOrNull { it.distanceTo(point) } ?: Double.POSITIVE_INFINITY
                schoolZone.update(schoolDistance) {
                    speak("school", "Precaución, zona escolar cercana")
                }
            }

            val direction = heading ?: return@collect
            val speed = posted.fresh(now, point, 30.0)
                ?.takeIf { cornerDifference(direction, it.heading) <= 35 && it.point.distanceTo(point) <= 25 }?.kmh
                ?: catalog?.takeUnless { it.osmStale }
                    ?.speedSections?.filter { it.distanceTo(point) <= 16 }?.map { it.kmh }?.distinct()?.singleOrNull()
            if (speed != null && speed != lastLimit &&
                speak("speed", "Límite de velocidad en esta vía: $speed kilómetros por hora")) {
                lastLimit = speed
            }

            val previousAlert = lastTrafficAnnouncement
            if ((previousAlert == null || now - previousAlert >= 90_000) &&
                traffic.fresh(now, point, 450.0)?.isAhead(point, direction) == true &&
                speak("traffic", "Tráfico adelante")) {
                lastTrafficAnnouncement = now
            }
        }
    }

    private suspend fun <T> fetch(base: String, path: String, token: String?, parse: (JsonElement) -> T?): LookupResult<T>? =
        try {
            val response = api.request(base, path, token, method = "GET")
            if (response.code == 200) LookupResult(parse(json.parseToJsonElement(response.body)))
            else if (response.code == 429) LookupResult(null,
                (response.retryAfter?.toLongOrNull()?.coerceIn(5, 3600) ?: 60) * 1000)
            else null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
}

private data class LookupResult<T>(val value: T?, val retryAfter: Long = 0)

private class RoadLookup<T>(private val ttl: Long) {
    private var origin: RoadPoint? = null
    private var value: T? = null
    private var expiresAt = 0L

    fun fresh(now: Long, position: RoadPoint? = null, radius: Double = Double.POSITIVE_INFINITY): T? =
        value.takeIf { now < expiresAt && (position == null || origin?.distanceTo(position)?.let { it <= radius } == true) }

    suspend fun poll(
        position: () -> RoadPoint?, distance: Double, interval: Long, refresh: Long,
        direction: () -> Int? = { null },
        request: suspend (RoadPoint) -> LookupResult<T>?,
    ) {
        var lastRequest: RoadPoint? = null
        var lastHeading: Int? = null
        var lastAt = 0L
        while (true) {
            val point = position()
            val now = SystemClock.elapsedRealtime()
            val heading = direction()
            val changedDirection = lastHeading?.let { heading != null && cornerDifference(heading, it) > 35 } == true
            if (point != null && (lastRequest == null || now - lastAt >= refresh ||
                    lastRequest.distanceTo(point) >= distance || changedDirection)) {
                lastRequest = point
                lastHeading = heading
                lastAt = now
                val result = request(point)
                if (result != null && result.retryAfter == 0L) {
                    origin = point
                    value = result.value
                    expiresAt = SystemClock.elapsedRealtime() + ttl
                }
                delay(maxOf(interval, result?.retryAfter ?: interval))
            } else {
                delay(500)
            }
        }
    }
}

private data class PostedLimit(val kmh: Int, val point: RoadPoint, val heading: Int)

private fun JsonObject.point(lat: String, lon: String): RoadPoint? {
    val latitude = get(lat)?.jsonPrimitive?.doubleOrNull ?: return null
    val longitude = get(lon)?.jsonPrimitive?.doubleOrNull ?: return null
    return RoadPoint(latitude, longitude).takeIf(RoadPoint::valid)
}

private fun JsonElement.matchedPosition(): RoadPoint? {
    if (this is JsonObject) return point("lat", "lon") ?: point("latitude", "longitude")
    val parts = jsonPrimitive.contentOrNull?.split(',') ?: return null
    if (parts.size != 2) return null
    val lat = parts[0].toDoubleOrNull() ?: return null
    val lon = parts[1].toDoubleOrNull() ?: return null
    return RoadPoint(lat, lon).takeIf(RoadPoint::valid)
}

private fun RoadPoint.ahead(heading: Int, meters: Double): RoadPoint {
    val radians = Math.toRadians(heading.toDouble())
    return RoadPoint(lat + meters * cos(radians) / 111_195,
        lon + meters * sin(radians) / (111_195 * cos(Math.toRadians(lat))))
}

private fun cornerDifference(first: Int, second: Int): Int =
    abs((first - second + 540) % 360 - 180)

internal data class Congestion(val line: List<RoadPoint>, val heading: Int) {
    fun isAhead(position: RoadPoint, direction: Int): Boolean {
        if (cornerDifference(direction, heading) > 35) return false
        val radians = Math.toRadians(direction.toDouble())
        val east = sin(radians)
        val north = cos(radians)
        return line.zipWithNext().any { (start, end) ->
            val (x, y) = start.offsetFrom(position)
            val (dx, dy) = end.offsetFrom(start)
            val length = hypot(dx, dy)
            val progress = dx * east + dy * north
            if (length < 1 || progress / length < 0.8) return@any false
            val ahead = x * east + y * north
            val from = maxOf(0.0, (35.0 - ahead) / progress)
            val to = minOf(1.0, (450.0 - ahead) / progress)
            if (from > to) return@any false
            val lateral = x * north - y * east
            val drift = dx * north - dy * east
            val startOffset = lateral + from * drift
            val endOffset = lateral + to * drift
            minOf(startOffset, endOffset) <= 25.0 && maxOf(startOffset, endOffset) >= -25.0
        }
    }
}

internal class SchoolProximity {
    private var inside = false

    fun update(distance: Double?, announce: () -> Boolean) {
        when {
            distance == null -> Unit
            distance <= 120 && !inside -> inside = announce()
            distance > 150 -> inside = false
        }
    }
}
