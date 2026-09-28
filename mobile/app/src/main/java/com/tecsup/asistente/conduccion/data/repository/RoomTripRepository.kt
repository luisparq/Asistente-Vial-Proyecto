package com.tecsup.asistente.conduccion.data.repository

import androidx.room.withTransaction
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.local.DrivingEventEntity
import com.tecsup.asistente.conduccion.data.local.TripEntity
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
import com.tecsup.asistente.conduccion.domain.model.DrivingEvent
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.Trip
import com.tecsup.asistente.conduccion.domain.model.TripStatus
import com.tecsup.asistente.conduccion.domain.model.TripSummary
import com.tecsup.asistente.conduccion.domain.model.TripWithEvents
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class RoomTripRepository @Inject constructor(
    private val database: AsistenteVialDatabase,
    private val clock: Clock,
) : TripRepository {
    private val dao = database.tripDao()

    override fun observeActiveTrip() = dao.observeActive().map { it?.toDomain() }

    override fun observeHistory() = dao.observeHistory().map { rows ->
        rows.map { TripSummary(it.trip.toDomain(), it.eventCount) }
    }

    override fun observeTrip(id: Long) = dao.observeTrip(id).map { row ->
        row?.let {
            TripWithEvents(it.trip.toDomain(), it.events
                .sortedWith(compareBy<DrivingEventEntity> { event -> event.occurredAt }.thenBy { event -> event.id })
                .map { event -> event.toDomain() })
        }
    }

    override suspend fun startOrResumeTrip(): Trip = database.withTransaction {
        dao.findActive()?.toDomain() ?: run {
            val entity = TripEntity(startedAt = clock.millis())
            entity.copy(id = dao.insertTrip(entity)).toDomain()
        }
    }

    override suspend fun addSimulatedEvent(tripId: Long, type: DrivingEventType): DrivingEvent =
        database.withTransaction {
            val trip = checkNotNull(dao.findTrip(tripId)) { "El recorrido no existe." }
            check(trip.status == TripStatus.ACTIVE.name) { "El recorrido ya finalizó." }
            val event = DrivingEventEntity(
                tripId = tripId,
                type = type.name,
                occurredAt = clock.millis(),
                origin = DataOrigin.SIMULATED.name,
            )
            event.copy(id = dao.insertEvent(event)).toDomain()
        }

    override suspend fun finishTrip(tripId: Long) {
        database.withTransaction {
            val trip = checkNotNull(dao.findTrip(tripId)) { "El recorrido no existe." }
            if (trip.status == TripStatus.ACTIVE.name) {
                dao.finishTrip(tripId, clock.millis())
            }
        }
    }
}

private fun TripEntity.toDomain() = Trip(
    id = id, startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let(Instant::ofEpochMilli), status = TripStatus.valueOf(status),
)

private fun DrivingEventEntity.toDomain() = DrivingEvent(
    id = id, tripId = tripId, type = DrivingEventType.valueOf(type),
    occurredAt = Instant.ofEpochMilli(occurredAt), origin = DataOrigin.valueOf(origin),
)
