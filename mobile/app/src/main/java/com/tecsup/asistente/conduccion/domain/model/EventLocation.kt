package com.tecsup.asistente.conduccion.domain.model

import com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus

enum class LocationOrigin { GPS_NEO6M }
data class EventLocation(val latitude: Double, val longitude: Double, val receivedAtMillis: Long,
    val ageMillis: Long, val utcSeconds: Int?)
object EventLocationPolicy {
    const val MAX_AGE_MILLIS = 2000L // Conservative development window, not certified GPS accuracy.
    fun capture(gps: GpsTelemetry?, eventAtMillis: Long, nowMonotonicMillis: Long): EventLocation? {
        if (gps == null || gps.status != GpsStatus.VALID) return null
        val lat = gps.latitude ?: return null; val lon = gps.longitude ?: return null
        val received = gps.receivedAtMillis ?: return null
        val monotonic = gps.receivedAtMonotonicMillis ?: return null
        val age = gps.ageMillis ?: return null
        val elapsed = nowMonotonicMillis - monotonic
        val wallElapsed = eventAtMillis - received
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
            age !in 0..MAX_AGE_MILLIS || elapsed !in 0..MAX_AGE_MILLIS ||
            wallElapsed !in 0..MAX_AGE_MILLIS || age + elapsed > MAX_AGE_MILLIS ||
            age + wallElapsed > MAX_AGE_MILLIS) return null
        return EventLocation(lat, lon, received, age + maxOf(elapsed, wallElapsed), gps.utcSeconds)
    }
}
