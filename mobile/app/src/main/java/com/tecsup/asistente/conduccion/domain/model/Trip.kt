package com.tecsup.asistente.conduccion.domain.model

import java.time.Instant

enum class TripStatus { ACTIVE, COMPLETED }

data class Trip(
    val id: Long,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: TripStatus,
)

data class TripSummary(val trip: Trip, val eventCount: Int)
data class TripWithEvents(val trip: Trip, val events: List<DrivingEvent>)
