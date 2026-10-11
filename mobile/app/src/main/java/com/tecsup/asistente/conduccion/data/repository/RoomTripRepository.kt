package com.tecsup.asistente.conduccion.data.repository

import androidx.room.withTransaction
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.domain.model.*
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import com.tecsup.asistente.conduccion.domain.sync.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalCoroutinesApi::class)
class RoomTripRepository @Inject constructor(
    private val database: AsistenteVialDatabase,
    private val clock: Clock,
    private val session: SessionStore = MemorySessionStore(),
    private val scheduler: SyncScheduler = SyncScheduler {},
    private val sensors: com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource? = null,
) : TripRepository {
    private val dao = database.tripDao()
    private val sync = database.syncDao()

    override fun observeActiveTrip() = session.state.flatMapLatest { sync.active(it?.ownerKey) }.map { it?.toDomain() }
    override fun observeHistory() = session.state.flatMapLatest { sync.history(it?.ownerKey) }
        .map { rows -> rows.map { TripSummary(it.trip.toDomain(), it.eventCount) } }
    override fun observeTrip(id: Long) = session.state.flatMapLatest { current ->
        dao.observeTrip(id).map { row ->
            val owner = sync.trip(id)?.ownerKey
            row?.takeIf { owner == null || owner == current?.ownerKey }?.let {
                TripWithEvents(it.trip.toDomain(), it.events.sortedWith(compareBy<DrivingEventEntity> { e -> e.occurredAt }.thenBy { e -> e.id }).map { e -> e.toDomain() })
            }
        }
    }

    private suspend fun checkAccess(id: Long) {
        val owner = sync.trip(id)?.ownerKey
        check(owner == null || owner == session.state.value?.ownerKey) { "El recorrido pertenece a otra sesión. Inicia sesión con su propietario." }
    }

    override suspend fun startOrResumeTrip(): Trip = session.gate.withLock {
        database.withTransaction {
            val active = dao.findActive()
            if (active != null) {
                checkAccess(active.id)
                active.toDomain()
            } else {
                val initial = TripEntity(startedAt = clock.millis())
                val trip = initial.copy(id = dao.insertTrip(initial))
                val meta = TripSyncEntity(trip.id, UUID.randomUUID().toString(), session.state.value?.ownerKey, 1)
                sync.insertTrip(meta)
                OutboxWriter.trip(database, trip, meta)
                trip.toDomain()
            }
        }
    }.also { scheduler.schedule() }

    override suspend fun addSimulatedEvent(tripId: Long, type: DrivingEventType): DrivingEvent = session.gate.withLock {
        database.withTransaction {
            checkAccess(tripId)
            val trip = checkNotNull(dao.findTrip(tripId)) { "El recorrido no existe." }
            check(trip.status == TripStatus.ACTIVE.name) { "El recorrido ya finalizó." }
            val occurredAt = clock.millis()
            val location = EventLocationPolicy.capture(sensors?.gps?.value, occurredAt, System.nanoTime() / 1_000_000)
            val initial = DrivingEventEntity(tripId = tripId, type = type.name, occurredAt = occurredAt, origin = DataOrigin.SIMULATED.name,
                latitude = location?.latitude, longitude = location?.longitude,
                locationOrigin = location?.let { LocationOrigin.GPS_NEO6M.name }, gpsUtcSeconds = location?.utcSeconds,
                gpsReceivedAt = location?.receivedAtMillis, gpsAgeMillis = location?.ageMillis)
            val event = initial.copy(id = dao.insertEvent(initial))
            val uuid = UUID.randomUUID().toString()
            sync.insertEvent(EventSyncEntity(event.id, uuid))
            OutboxWriter.event(database, event, checkNotNull(sync.trip(tripId)).clientTripId, uuid)
            event.toDomain()
        }
    }.also { scheduler.schedule() }

    override suspend fun finishTrip(tripId: Long) {
        session.gate.withLock {
            database.withTransaction {
                checkAccess(tripId)
                val trip = checkNotNull(dao.findTrip(tripId)) { "El recorrido no existe." }
                if (trip.status == TripStatus.ACTIVE.name) {
                    dao.finishTrip(tripId, clock.millis())
                    sync.revision(tripId, 2)
                    OutboxWriter.trip(database, checkNotNull(dao.findTrip(tripId)), checkNotNull(sync.trip(tripId)))
                }
            }
        }
        scheduler.schedule()
    }
}

private fun TripEntity.toDomain() = Trip(id, Instant.ofEpochMilli(startedAt), endedAt?.let(Instant::ofEpochMilli), TripStatus.valueOf(status))
private fun DrivingEventEntity.toDomain() = DrivingEvent(id, tripId, DrivingEventType.valueOf(type), Instant.ofEpochMilli(occurredAt), DataOrigin.valueOf(origin), latitude, longitude, locationOrigin?.let(LocationOrigin::valueOf), gpsUtcSeconds, gpsReceivedAt?.let(Instant::ofEpochMilli), gpsAgeMillis)
