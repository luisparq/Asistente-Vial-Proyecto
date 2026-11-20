package com.tecsup.asistente.conduccion.data.repository

import androidx.room.withTransaction
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry
import com.tecsup.asistente.conduccion.domain.model.*
import com.tecsup.asistente.conduccion.domain.repository.StartedTrip
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import com.tecsup.asistente.conduccion.domain.sync.*
import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.network.TrackPointDto
import com.tecsup.asistente.conduccion.domain.processing.IdentifiedManeuver
import com.tecsup.asistente.conduccion.domain.processing.ManeuverAlertRule
import java.time.Clock
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class RoomTripRepository @Inject constructor(
    private val database: AsistenteVialDatabase,
    private val clock: Clock,
    private val session: SessionStore = MemorySessionStore(),
    private val scheduler: SyncScheduler = SyncScheduler {},
    private val sensors: com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource? = null,
    private val maneuverAlertRule: ManeuverAlertRule = ManeuverAlertRule(),
) : TripRepository {
    private val dao = database.tripDao()
    private val sync = database.syncDao()
    private var latestTrackFix: Pair<Long, EventLocation>? = null

    override fun observeActiveTrip() = session.state.flatMapLatest { sync.active(it?.ownerKey) }.map { it?.toDomain() }
    override fun observeConfirmedManeuverAlert(tripId: Long) = session.state.flatMapLatest { current ->
        if (current != null && sync.trip(tripId)?.ownerKey == current.ownerKey) sync.confirmedManeuverAlert(tripId)
        else kotlinx.coroutines.flow.flowOf(null)
    }

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

    suspend fun recordTrackPoint(gps: GpsTelemetry) {
        val fix = EventLocationPolicy.capture(gps, clock.millis(), System.nanoTime() / 1_000_000) ?: return
        val saved = session.gate.withLock {
            database.withTransaction {
                val trip = dao.findActive() ?: return@withTransaction false
                val meta = sync.trip(trip.id) ?: return@withTransaction false
                if (meta.ownerKey != session.state.value?.ownerKey && meta.ownerKey != null) return@withTransaction false
                val previousFix = latestTrackFix?.takeIf { it.first == trip.id }?.second
                if (fix.receivedAtMillis >= trip.startedAt &&
                    (previousFix == null || fix.receivedAtMillis >= previousFix.receivedAtMillis)
                ) {
                    latestTrackFix = trip.id to fix
                }
                TrackPointWriter.record(database, trip, fix)
            }
        }
        if (saved) scheduler.scheduleFallback()
    }

    override suspend fun startOrResume(): StartedTrip = session.gate.withLock {
        database.withTransaction {
            val active = dao.findActive()
            if (active != null) {
                checkAccess(active.id)
                StartedTrip(active.toDomain(), created = false)
            } else {
                val initial = TripEntity(startedAt = clock.millis())
                val trip = initial.copy(id = dao.insertTrip(initial))
                val meta = TripSyncEntity(trip.id, UUID.randomUUID().toString(), session.state.value?.ownerKey, 1)
                sync.insertTrip(meta)
                OutboxWriter.trip(database, trip, meta)
                StartedTrip(trip.toDomain(), created = true)
            }
        }
    }.also { scheduler.schedule() }

    suspend fun recordDemonstration(type: DrivingEventType) {
        session.gate.withLock {
            val owner = checkNotNull(session.state.value?.takeIf { !it.token.isNullOrBlank() }?.ownerKey)
            database.withTransaction {
                val now = clock.millis()
                val initial = TripEntity(startedAt = now, endedAt = now, status = TripStatus.COMPLETED.name, activeSlot = null)
                val trip = initial.copy(id = dao.insertTrip(initial))
                val meta = TripSyncEntity(trip.id, UUID.randomUUID().toString(), owner, 2)
                sync.insertTrip(meta)
                OutboxWriter.trip(database, trip, meta)
                val candidate = DrivingEventEntity(tripId = trip.id, type = type.name, occurredAt = now, origin = DataOrigin.SIMULATED.name)
                val event = candidate.copy(id = dao.insertEvent(candidate))
                val uuid = UUID.randomUUID().toString()
                sync.insertEvent(EventSyncEntity(event.id, uuid))
                OutboxWriter.event(database, event, meta.clientTripId, uuid)
            }
        }
        scheduler.schedule()
    }

    override suspend fun addSimulatedEvent(tripId: Long, type: DrivingEventType): DrivingEvent =
        recordSimulatedManeuver(tripId, type).first

    override suspend fun simulateManeuver(tripId: Long, type: DrivingEventType): String? =
        recordSimulatedManeuver(tripId, type).second

    private suspend fun recordSimulatedManeuver(
        tripId: Long, type: DrivingEventType,
    ) = session.gate.withLock {
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
            val ids = maneuverAlertRule.triggeringIds(
                dao.simulatedManeuvers(tripId),
                IdentifiedManeuver(uuid, occurredAt),
                dao.latestManeuverAlert(tripId)?.occurredAt,
            )
            val alertId = if (ids.isEmpty()) null else storeManeuverAlert(tripId, ids, occurredAt, location)
            event.toDomain() to alertId
        }
    }.also { scheduler.schedule() }

    private suspend fun storeManeuverAlert(
        tripId: Long, ids: List<String>, occurredAt: Long, current: EventLocation?,
    ): String {
        val recordedPoint = sync.lastTrackPoint(tripId, TrackPointWriter.REVISION)?.let {
            Contract.json.decodeFromString<TrackPointDto>(it.payload)
        }
        val recent = listOfNotNull(
            current?.let { TrackPointDto(Contract.timestamp(it.receivedAtMillis), it.latitude, it.longitude) },
            latestTrackFix?.takeIf { it.first == tripId }?.second?.let {
                TrackPointDto(Contract.timestamp(it.receivedAtMillis), it.latitude, it.longitude)
            },
            recordedPoint,
        ).map { it to Instant.parse(it.captured_at).toEpochMilli() }
            .filter { (_, capturedAt) -> capturedAt <= occurredAt }
            .maxByOrNull { it.second }

        val id = UUID.randomUUID().toString()
        val alert = ManeuverAlertEntity(
            id, tripId, Contract.json.encodeToString(ids), occurredAt, testOnly = true,
            latitude = recent?.first?.latitude, longitude = recent?.first?.longitude,
            locationCapturedAt = recent?.second, locationStatus = if (recent == null) "MISSING" else "AVAILABLE",
        )
        dao.insertManeuverAlert(alert)
        OutboxWriter.maneuverAlert(database, alert, checkNotNull(sync.trip(tripId)).clientTripId)
        return id
    }

    override suspend fun finishTrip(tripId: Long) {
        session.gate.withLock {
            database.withTransaction {
                checkAccess(tripId)
                val trip = checkNotNull(dao.findTrip(tripId)) { "El recorrido no existe." }
                if (trip.status == TripStatus.ACTIVE.name) {
                    val endedAt = clock.millis()
                    val fix = EventLocationPolicy.capture(sensors?.gps?.value, endedAt, System.nanoTime() / 1_000_000)
                    TrackPointWriter.record(database, trip, fix, force = true)
                    dao.finishTrip(tripId, endedAt)
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
