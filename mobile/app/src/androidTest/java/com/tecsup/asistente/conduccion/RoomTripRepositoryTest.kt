package com.tecsup.asistente.conduccion

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.local.DrivingEventEntity
import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.TripStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RoomTripRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AsistenteVialDatabase
    private lateinit var repository: RoomTripRepository
    private lateinit var name: String
    private val clock = TestClock()

    @Before
    fun setup() {
        name = "room-test-${System.nanoTime()}.db"
        openDatabase()
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, name).build()
        repository = RoomTripRepository(database, clock)
    }

    @After
    fun cleanup() {
        database.close()
        context.deleteDatabase(name) // Solo el archivo exclusivo de esta prueba.
    }

    @Test
    fun lifecycleAndFlowPersistAllEventTypes() = runBlocking {
        withTimeout(15_000) {
            assertTrue(repository.observeHistory().first().isEmpty())
            val trip = repository.startOrResumeTrip()
            assertTrue(trip.id > 0)
            assertEquals(TripStatus.ACTIVE, trip.status)
            assertEquals(clock.instant(), trip.startedAt)
            assertNull(trip.endedAt)
            val updated = async { repository.observeTrip(trip.id).first { it?.events?.size == 3 } }
            DrivingEventType.entries.forEach { type ->
                clock.advance()
                val event = repository.addSimulatedEvent(trip.id, type)
                assertEquals(trip.id, event.tripId)
                assertEquals(DataOrigin.SIMULATED, event.origin)
                assertEquals(clock.instant(), event.occurredAt)
            }
            assertEquals(DrivingEventType.entries.toList(), updated.await()!!.events.map { it.type })
            clock.advance()
            repository.finishTrip(trip.id)
            val saved = repository.observeTrip(trip.id).first()!!
            assertEquals(TripStatus.COMPLETED, saved.trip.status)
            assertEquals(clock.instant(), saved.trip.endedAt)
            assertNull(repository.observeActiveTrip().first())
            assertEquals(3, repository.observeHistory().first().single().eventCount)
        }
    }

    @Test
    fun authenticatedDemonstrationQueuesATestEventOutsideTheActiveTrip() = runBlocking {
        val session = com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore()
        val account = com.tecsup.asistente.conduccion.domain.sync.Session(
            "http://127.0.0.1/", 1, 2, "TEST", "test@example.invalid", "TEST-token",
        )
        session.save(account)
        val trips = RoomTripRepository(database, clock, session)
        val active = trips.startOrResumeTrip()
        clock.advance()
        trips.recordDemonstration(DrivingEventType.SHARP_TURN)

        assertEquals(active, trips.observeActiveTrip().first())
        assertTrue(trips.observeTrip(active.id).first()!!.events.isEmpty())
        val demo = trips.observeHistory().first().single { it.trip.status == TripStatus.COMPLETED }
        assertEquals(account.ownerKey, database.syncDao().trip(demo.trip.id)?.ownerKey)
        val event = trips.observeTrip(demo.trip.id).first()!!.events.single()
        assertEquals(DataOrigin.SIMULATED, event.origin)
        assertEquals(DrivingEventType.SHARP_TURN, event.type)
        val queue = database.syncDao().all().filter { it.localTripId == demo.trip.id }
        assertEquals(2, queue.size)
        assertTrue(queue.any { it.revision == 2 })
        assertTrue(queue.any { it.eventUuid != null })
    }

    @Test
    fun concurrentStartsResumeOneTripAndFinishIsIdempotent() = runBlocking {
        withTimeout(15_000) {
            val ids = (1..12).map {
                async(Dispatchers.Default) { repository.startOrResumeTrip().id }
            }.awaitAll()
            assertEquals(1, ids.toSet().size)
            assertEquals(1, repository.observeHistory().first().size)
            val id = ids.first()
            repository.finishTrip(id)
            val firstEnd = repository.observeTrip(id).first()!!.trip.endedAt
            clock.advance()
            repository.finishTrip(id)
            assertEquals(firstEnd, repository.observeTrip(id).first()!!.trip.endedAt)
            val error = runCatching { repository.addSimulatedEvent(id, DrivingEventType.SHARP_TURN) }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertTrue(repository.observeTrip(id).first()!!.events.isEmpty())
            assertNotEquals(id, repository.startOrResumeTrip().id)
        }
    }

    @Test
    fun fileReopenPreservesActiveAndCompletedTrips() = runBlocking {
        withTimeout(15_000) {
            val trip = repository.startOrResumeTrip()
            val event = repository.addSimulatedEvent(trip.id, DrivingEventType.HARSH_BRAKING)
            database.close()
            openDatabase()
            assertEquals(trip, repository.observeActiveTrip().first())
            assertEquals(listOf(event), repository.observeTrip(trip.id).first()!!.events)
            assertEquals(trip.id, repository.startOrResumeTrip().id)
            clock.advance()
            repository.finishTrip(trip.id)
            val beforeClose = repository.observeTrip(trip.id).first()
            database.close()
            openDatabase()
            assertEquals(beforeClose, repository.observeTrip(trip.id).first())
            assertEquals(1, repository.observeHistory().first().size)
            assertNull(repository.observeActiveTrip().first())
        }
    }

    @Test
    fun foreignKeyRejectsOrphanEvents() = runBlocking {
        val failure = runCatching {
            database.tripDao().insertEvent(DrivingEventEntity(
                tripId = 999, type = DrivingEventType.SHARP_TURN.name,
                occurredAt = clock.millis(), origin = DataOrigin.SIMULATED.name,
            ))
        }.exceptionOrNull()
        assertTrue(failure is SQLiteConstraintException)
        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun repeatedManeuversPersistWithoutNetworkAndStayWithinTheirTrip() = runBlocking {
        val tripId = repository.startOrResumeTrip().id
        val types = DrivingEventType.entries
        repeat(3) {
            clock.advance(3)
            assertNull(repository.simulateManeuver(tripId, types[it]))
        }
        database.close()
        openDatabase()
        clock.advance(3)
        val alertId = checkNotNull(repository.simulateManeuver(tripId, types.first()))
        val saved = checkNotNull(database.tripDao().latestManeuverAlert(tripId))
        assertEquals(alertId, saved.id)
        assertEquals(tripId, saved.tripId)
        assertEquals(clock.millis(), saved.occurredAt)
        assertTrue(saved.testOnly)
        assertEquals("MISSING", saved.locationStatus)
        assertNull(saved.latitude)
        val ids = database.tripDao().simulatedManeuvers(tripId).map { it.eventId }
        assertEquals(ids, Contract.json.decodeFromString<List<String>>(saved.eventIds))

        database.close()
        openDatabase()
        assertEquals(saved, database.tripDao().latestManeuverAlert(tripId))
        clock.advance(3)
        assertNull(repository.simulateManeuver(tripId, types.first()))
        repository.finishTrip(tripId)
        val newTrip = repository.startOrResumeTrip().id
        clock.advance(3)
        assertNull(repository.simulateManeuver(newTrip, types.first()))
        assertNull(database.tripDao().latestManeuverAlert(newTrip))
    }

    @Test
    fun repeatedManeuverAlertStoresTheMostRecentValidFix() = runBlocking {
        val tripId = repository.startOrResumeTrip().id
        val earlier = GpsTelemetry(
            status = GpsStatus.VALID, latitude = -16.4, longitude = -71.5, ageMillis = 0,
            receivedAtMillis = clock.millis(), receivedAtMonotonicMillis = System.nanoTime() / 1_000_000,
        )
        repository.recordTrackPoint(earlier)
        clock.advance(2)
        val latest = earlier.copy(
            latitude = -16.5, receivedAtMillis = clock.millis(),
            receivedAtMonotonicMillis = System.nanoTime() / 1_000_000,
        )
        repository.recordTrackPoint(latest)
        repository.recordTrackPoint(GpsTelemetry(status = GpsStatus.NO_FIX))

        repeat(4) {
            clock.advance(3)
            repository.simulateManeuver(tripId, DrivingEventType.SHARP_TURN)
        }

        val saved = checkNotNull(database.tripDao().latestManeuverAlert(tripId))
        assertEquals("AVAILABLE", saved.locationStatus)
        assertEquals(latest.latitude, saved.latitude)
        assertEquals(latest.longitude, saved.longitude)
        assertEquals(latest.receivedAtMillis, saved.locationCapturedAt)
    }

    private class TestClock : Clock() {
        private var now = Instant.parse("2026-01-01T12:00:00Z")
        fun advance(seconds: Long = 1) { now = now.plusSeconds(seconds) }
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
}
