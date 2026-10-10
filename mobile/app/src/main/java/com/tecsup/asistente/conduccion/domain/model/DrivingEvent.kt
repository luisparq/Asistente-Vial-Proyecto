package com.tecsup.asistente.conduccion.domain.model

import java.time.Instant

/** Tipos preliminares de prueba, no clasificaciones de infracciones. */
enum class DrivingEventType { HARSH_BRAKING, HARSH_ACCELERATION, SHARP_TURN }

/** SENSOR queda reservado; no existe un generador ni integración de sensores en Etapa 2. */
enum class DataOrigin { SIMULATED, SENSOR }

data class DrivingEvent(
    val id: Long,
    val tripId: Long,
    val type: DrivingEventType,
    val occurredAt: Instant,
    val origin: DataOrigin,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationOrigin: LocationOrigin? = null,
    val gpsUtcSeconds: Int? = null,
    val gpsReceivedAt: Instant? = null,
    val gpsAgeMillis: Long? = null,
)
