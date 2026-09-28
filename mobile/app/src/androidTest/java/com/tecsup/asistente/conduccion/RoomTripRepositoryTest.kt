package com.tecsup.asistente.conduccion

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.local.DrivingEventEntity
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
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

    private class TestClock : Clock() {
        private var now = Instant.parse("2026-01-01T12:00:00Z")
        fun advance() { now = now.plusSeconds(1) }
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
    }
}
