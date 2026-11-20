package com.tecsup.asistente.conduccion.feature.map

import kotlin.math.*
import kotlinx.serialization.Serializable

@Serializable
data class RoadPoint(val lat: Double, val lon: Double) {
    fun valid() = lat in -90.0..90.0 && lon in -180.0..180.0

    fun offsetFrom(origin: RoadPoint): Pair<Double, Double> =
        (lon - origin.lon) * 111_195 * cos(Math.toRadians(origin.lat)) to
            (lat - origin.lat) * 111_195

    fun distanceTo(other: RoadPoint): Double {
        val (x, y) = offsetFrom(other)
        return hypot(x, y)
    }
}
@Serializable
data class RoadZone(val id: String, val name: String, val point: RoadPoint, val radiusM: Int)

@Serializable
data class SpeedSection(val id: String, val start: RoadPoint, val end: RoadPoint, val kmh: Int) {
    fun distanceTo(position: RoadPoint): Double {
        val (x, y) = end.offsetFrom(start)
        val (px, py) = position.offsetFrom(start)
        val lengthSquared = x * x + y * y
        if (lengthSquared == 0.0) return Double.POSITIVE_INFINITY
        val fraction = (px * x + py * y) / lengthSquared
        if (fraction !in 0.0..1.0) return Double.POSITIVE_INFINITY
        return hypot(px - fraction * x, py - fraction * y)
    }
}

@Serializable
data class RoadTraffic(
    val id: String,
    val point: RoadPoint,
    val directionDegrees: Int,
    val expiresAt: Long,
)

@Serializable
data class RoadCatalog(
    val zones: List<RoadZone> = emptyList(),
    val speedSections: List<SpeedSection> = emptyList(),
    val traffic: List<RoadTraffic> = emptyList(),
    val osmAvailable: Boolean = true,
    val trafficAvailable: Boolean = false,
    val osmStale: Boolean = false,
) {
    fun validated() = copy(
        zones = zones.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.point.valid() && it.radiusM in 20..500 }
            .distinctBy { it.id },
        speedSections = speedSections.filter {
            it.id.isNotBlank() && it.start.valid() && it.end.valid() &&
                it.end.distanceTo(it.start) in 1.0..2_000.0 && it.kmh in 5..150
        }.distinctBy { it.id },
        traffic = traffic.filter {
            it.id.isNotBlank() && it.point.valid() && it.directionDegrees in 0..359
        }.distinctBy { it.id },
    )

    fun activeAt(now: Long) = copy(traffic = traffic.filter { it.expiresAt > now })

    fun alerts(position: RoadPoint, previous: RoadPoint?): Map<String, String> = buildMap {
        zones.filter { it.point.distanceTo(position) <= it.radiusM }.forEach {
            put("zone:${it.id}", "Atención, ${it.name}")
        }

        if (!osmStale) {
            val limits = speedSections.filter { it.distanceTo(position) <= 16 }.map { it.kmh }.distinct()
            limits.singleOrNull()?.let { put("speed:$it", "Límite de velocidad en esta vía: $it kilómetros por hora") }
        }

        if (previous != null) {
            val (dx, dy) = position.offsetFrom(previous)
            val movement = hypot(dx, dy)
            if (movement in 8.0..150.0) {
                traffic.forEach { item ->
                    val radians = Math.toRadians(item.directionDegrees.toDouble())
                    val east = sin(radians)
                    val north = cos(radians)
                    val (x, y) = item.point.offsetFrom(position)
                    val ahead = x * east + y * north
                    val lateral = abs(x * north - y * east)
                    val movingToward = (dx * east + dy * north) / movement >= 0.85
                    if (ahead in 35.0..450.0 && lateral <= 25 && movingToward) {
                        put("traffic:${item.id}", "Tráfico adelante")
                    }
                }
            }
        }
    }
}
