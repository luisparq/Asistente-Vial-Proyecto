package com.tecsup.asistente.conduccion.domain.diagnostic

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class GpsStatus { DISCONNECTED, UNAVAILABLE, WAITING, NO_RECEPTION, NO_FIX, VALID, STALE, INVALID_FRAME }
data class GpsTelemetry(val status: GpsStatus = GpsStatus.DISCONNECTED, val latitude: Double? = null,
    val longitude: Double? = null, val utcSeconds: Int? = null, val ageMillis: Long? = null,
    val satellites: Int? = null, val receivedAtMillis: Long? = null, val receivedAtMonotonicMillis: Long? = null)
object GpsDecoder {
    const val FRAME_SIZE = 20
    const val MAX_AGE_MILLIS = 5000L
    fun decode(bytes: ByteArray): GpsTelemetry {
        require(bytes.size == FRAME_SIZE)
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(b.get().toInt() == 1)
        val status = when (b.get().toInt()) {
            0 -> GpsStatus.NO_RECEPTION; 1 -> GpsStatus.NO_FIX; 2 -> GpsStatus.VALID; 3 -> GpsStatus.STALE
            else -> throw IllegalArgumentException("Estado GPS desconocido")
        }
        val flags = b.get().toInt() and 255
        require(flags and 252 == 0)
        val sats = b.get().toInt() and 255
        val lat = b.int / 1e7; val lon = b.int / 1e7
        val utc = b.int.toLong() and 0xffffffffL
        val age = b.int.toLong() and 0xffffffffL
        val valid = status == GpsStatus.VALID && age <= MAX_AGE_MILLIS
        if (status == GpsStatus.VALID) {
            require(lat in -90.0..90.0 && lon in -180.0..180.0)
            if (flags and 1 != 0) require(utc < 86400)
        } else require(flags and 1 == 0)
        if (flags and 2 != 0) require(sats <= 99)
        return GpsTelemetry(if (status == GpsStatus.VALID && !valid) GpsStatus.STALE else status,
            lat.takeIf { valid }, lon.takeIf { valid }, utc.toInt().takeIf { valid && flags and 1 != 0 },
            age.takeUnless { it == 0xffffffffL }, sats.takeIf { flags and 2 != 0 })
    }
}
