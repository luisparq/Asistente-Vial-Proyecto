package com.tecsup.asistente.conduccion

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.data.sync.*
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsTelemetry
import com.tecsup.asistente.conduccion.domain.sync.*
import com.tecsup.asistente.conduccion.domain.processing.ManeuverAlertRule
import java.time.Clock
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** HTTP responses in this class are MOCKED. Real Laravel tests live separately. */
class SyncIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var name: String
    private lateinit var db: AsistenteVialDatabase
    private lateinit var server: MockWebServer
    private val session = MemorySessionStore()
    private val scheduler = SyncScheduler {}
    private val api = LaravelApi()
    private val clock = Clock.systemUTC()
    private fun repo() = RoomTripRepository(db, clock, session, scheduler)
    private fun engine() = SyncEngine(db, session, api, clock)
    private fun open() { db = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build() }
    @Before fun setup() {
        name = "sync-test-${UUID.randomUUID()}.db"
        open()
        server = MockWebServer().apply { start() }
        session.save(Session(server.url("/").toString(), 1, 1, "TEST ONLY", "test@example.invalid", "TEST-only-token"))
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name); server.shutdown() }

    private suspend fun ack(op: OutboxEntity): MockResponse {
        val meta = db.syncDao().trip(op.localTripId)!!
        val payload = Contract.json.parseToJsonElement(op.payload).jsonObject
        val data = buildJsonObject {
            payload.forEach { (k, v) -> put(k, v) }
            put("client_trip_id", meta.clientTripId)
            op.eventUuid?.let { put("client_event_id", it) }
            if (op.revision == OutboxWriter.MANEUVER_ALERT_REVISION) put("client_alert_id", op.operationId)
        }
        return MockResponse().setResponseCode(200).setBody(buildJsonObject {
            put("data", data)
            putJsonObject("sync") {
                put("disposition", "already_applied")
                if (op.eventUuid == null) { put("acknowledged_revision", op.revision); put("current_revision", op.revision) }
            }
        }.toString())
    }
    @Test fun activeCompletedAndSimulatedEventQueueAndReceipts() = runBlocking {
        val trip = repo().startOrResumeTrip()
        val first = db.syncDao().all().single()
        server.enqueue(ack(first))
        assertFalse(engine().drain())
        assertEquals("ACKED", db.syncDao().all().single().state)
        repo().addSimulatedEvent(trip.id, DrivingEventType.HARSH_BRAKING)
        repo().finishTrip(trip.id)
        val queued = db.syncDao().all().filter { it.state != "ACKED" }
        server.enqueue(ack(queued.single { it.eventUuid == null }))
        server.enqueue(ack(queued.single { it.eventUuid != null }))
        engine().drain()
        assertTrue(db.syncDao().all().all { it.state == "ACKED" })
        assertEquals(3, server.requestCount)
        assertEquals("Bearer TEST-only-token", server.takeRequest().getHeader("Authorization"))
        assertTrue(server.takeRequest().body.readUtf8().contains("COMPLETED"))
        assertTrue(server.takeRequest().body.readUtf8().contains("SIMULATED"))
    }
    @Test fun gpsPointsBelongToActiveTripAndSyncAfterTripReceipt() = runBlocking {
        val repository = repo()
        fun sample() = GpsTelemetry(
            status = GpsStatus.VALID, latitude = -16.39891, longitude = -71.53695, ageMillis = 0,
            receivedAtMillis = clock.millis(), receivedAtMonotonicMillis = System.nanoTime() / 1_000_000,
        )
        repository.recordTrackPoint(sample())
        assertTrue(db.syncDao().all().isEmpty())
        val trip = repository.startOrResumeTrip()
        val firstFix = sample()
        repository.recordTrackPoint(firstFix)
        repository.recordTrackPoint(firstFix)
        val queued = db.syncDao().all()
        assertEquals(2, queued.size)
        db.close()
        open()
        assertEquals(queued.map { it.operationId }, db.syncDao().all().map { it.operationId })
        server.enqueue(ack(queued.single { it.revision == 1 }))
        val point = queued.single { it.revision == TrackPointWriter.REVISION }
        server.enqueue(MockResponse().setResponseCode(201).setBody("{\"data\":${point.payload}}"))
        assertFalse(engine().drain())
        assertTrue(db.syncDao().all().all { it.state == "ACKED" })
        assertEquals("/api/v1/trips/${db.syncDao().trip(trip.id)!!.clientTripId}", server.takeRequest().path)
        assertEquals("/${point.path}", server.takeRequest().path)
        repo().finishTrip(trip.id)
        repo().recordTrackPoint(sample())
        assertEquals(1, db.syncDao().all().count { it.revision == TrackPointWriter.REVISION })
    }
    @Test fun maneuverAlertRetriesTheSameIdAndPayloadAfterItsEvents() = runBlocking {
        val repository = RoomTripRepository(db, clock, session, scheduler,
            maneuverAlertRule = ManeuverAlertRule(groupingMillis = 0))
        val trip = repository.startOrResumeTrip()
        repeat(3) { repository.simulateManeuver(trip.id, DrivingEventType.entries[it]) }
        val alertId = checkNotNull(repository.simulateManeuver(trip.id, DrivingEventType.SHARP_TURN))
        val operations = db.syncDao().all()
        val tripOperation = operations.single { it.revision == 1 }
        val maneuverOperations = operations.filter { it.eventUuid != null }
        val alert = operations.single { it.revision == OutboxWriter.MANEUVER_ALERT_REVISION }
        assertEquals(alertId, alert.operationId)
        server.enqueue(ack(tripOperation))
        // Events are sent in insertion order, irrespective of their randomly assigned IDs.
        for (event in db.tripDao().simulatedManeuvers(trip.id)) {
            server.enqueue(ack(maneuverOperations.single { it.eventUuid == event.eventId }))
        }
        server.enqueue(MockResponse().setResponseCode(425))
        assertTrue(engine().drain())
        assertEquals("RETRY_WAIT", db.syncDao().operation(alertId)?.state)
        repeat(5) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        val first = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/${alert.path}", first.path)
        assertEquals(alert.payload, first.body.readUtf8())

        db.close()
        open()
        server.enqueue(ack(alert))
        val afterBackoff = Clock.offset(clock, Duration.ofMinutes(5))
        assertFalse(SyncEngine(db, session, api, afterBackoff).drain())
        val retry = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals(first.path, retry.path)
        assertEquals(alert.payload, retry.body.readUtf8())
        assertEquals("ACKED", db.syncDao().operation(alertId)?.state)
        assertEquals(alertId, db.tripDao().latestManeuverAlert(trip.id)?.id)
    }

    @Test fun offlineQueueAndUuidSurviveReopenAndNetworkFailure() = runBlocking {
        val trip = repo().startOrResumeTrip()
        repo().addSimulatedEvent(trip.id, DrivingEventType.SHARP_TURN)
        repo().finishTrip(trip.id)
        val before = db.syncDao().all()
        val uuid = db.syncDao().trip(trip.id)!!.clientTripId
        server.shutdown()
        assertTrue(engine().drain())
        assertTrue(db.syncDao().all().any { it.state == "RETRY_WAIT" })
        db.close(); open()
        assertEquals(uuid, db.syncDao().trip(trip.id)!!.clientTripId)
        assertEquals(before.map { it.operationId }, db.syncDao().all().map { it.operationId })
        assertEquals(1, repo().observeTrip(trip.id).first()!!.events.size)
        assertEquals("COMPLETED", db.tripDao().findTrip(trip.id)!!.status)
    }
    @Test fun unauthorizedRetainsOwnershipAndStopsSending() = runBlocking {
        val trip = repo().startOrResumeTrip()
        server.enqueue(MockResponse().setResponseCode(401))
        engine().drain()
        assertNull(session.state.value!!.token)
        assertEquals(session.state.value!!.ownerKey, db.syncDao().trip(trip.id)!!.ownerKey)
        assertEquals("BLOCKED_AUTH", db.syncDao().all().single().state)
        repo().finishTrip(trip.id) // still works offline after token expiry
        engine().drain()
        assertEquals(1, server.requestCount)
    }
    @Test fun conflictValidationForbiddenAndRateLimitAreDurable() = runBlocking {
        for ((code, expected) in listOf(403 to "BLOCKED_AUTH", 409 to "BLOCKED_CONFLICT", 422 to "BLOCKED_VALIDATION", 429 to "RETRY_WAIT")) {
            val trip = repo().startOrResumeTrip()
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After", "120"))
            engine().drain()
            val op = db.syncDao().all().first { it.localTripId == trip.id }
            assertEquals(expected, op.state)
            if (code == 429) assertTrue(op.nextAttemptAt >= clock.millis() + 110_000)
            // Finish locally; block this second operation too so the next loop is independent.
            repo().finishTrip(trip.id)
            server.enqueue(MockResponse().setResponseCode(422))
            engine().drain()
        }
    }
    @Test fun expiredLeaseRetriesIdenticalPayloadAndIgnoresMalformedSuccess() = runBlocking {
        repo().startOrResumeTrip()
        val original = db.syncDao().all().single()
        db.syncDao().claim(original.operationId, "abandoned-test-lease", 0)
        db.close(); open()
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        engine().drain()
        assertEquals(original.payload, server.takeRequest().body.readUtf8())
        assertEquals("BLOCKED_CONFLICT", db.syncDao().all().single().state)
        assertEquals(original.operationId, db.syncDao().all().single().operationId)
    }
    @Test fun explicitAdoptionAndAccountIsolation() = runBlocking {
        val original = session.state.value!!
        session.save(null)
        val trip = repo().startOrResumeTrip()
        repo().addSimulatedEvent(trip.id, DrivingEventType.HARSH_ACCELERATION)
        repo().finishTrip(trip.id)
        session.save(original)
        engine().drain()
        assertEquals(0, server.requestCount)
        SyncAccount(db, session, api, scheduler).adopt(trip.id)
        assertEquals(original.ownerKey, db.syncDao().trip(trip.id)!!.ownerKey)
        session.save(original.copy(userId = 2))
        assertTrue(repo().observeHistory().first().isEmpty())
        assertNull(repo().observeTrip(trip.id).first())
        engine().drain()
        assertEquals(0, server.requestCount)
    }
    @Test fun migrationPreservesVersionOneRecordsAndGeneratesPersistentIds() = runBlocking {
        db.close()
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, status TEXT NOT NULL, activeSlot INTEGER)")
            old.execSQL("CREATE UNIQUE INDEX index_trips_activeSlot ON trips(activeSlot)")
            old.execSQL("CREATE TABLE driving_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, type TEXT NOT NULL, occurredAt INTEGER NOT NULL, origin TEXT NOT NULL, FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_driving_events_tripId ON driving_events(tripId)")
            old.execSQL("INSERT INTO trips VALUES(42,1000,2000,'COMPLETED',NULL)")
            old.execSQL("INSERT INTO driving_events VALUES(91,42,'SHARP_TURN',1500,'SIMULATED')")
            old.version = 1
        }
        open()
        assertEquals(1000L, db.tripDao().findTrip(42)!!.startedAt)
        assertEquals(91L, db.tripDao().events(42).single().id)
        val uuid = db.syncDao().trip(42)!!.clientTripId
        UUID.fromString(uuid)
        UUID.fromString(db.syncDao().event(91)!!.clientEventId)
        assertNull(db.syncDao().trip(42)!!.ownerKey)
        assertTrue(db.syncDao().all().isEmpty())
        db.close(); open()
        assertEquals(uuid, db.syncDao().trip(42)!!.clientTripId)
        SyncAccount(db, session, api, scheduler).adopt(42)
        assertEquals(2, db.syncDao().all().size)
    }

    @Test fun manualRetryAdvancesOnlyNetworkFailureAndRetainsIdentifiers() = runBlocking {
        val trip = repo().startOrResumeTrip()
        val original = db.syncDao().all().single()
        // Connection refused, not a mocked HTTP error response.
        val port = server.port
        server.shutdown()
        assertEquals(1, engine().syncNow().pending)
        val failed = db.syncDao().all().single()
        assertEquals("network_unavailable", failed.errorCode)
        assertTrue(failed.nextAttemptAt > clock.millis())
        db.close(); open()
        server = MockWebServer().apply { start(port) }
        server.enqueue(ack(original))
        assertEquals(1, engine().syncNow().acknowledged)
        val sent = server.takeRequest()
        assertEquals(original.path, sent.path!!.removePrefix("/"))
        assertEquals(original.payload, sent.body.readUtf8())
        assertEquals(original.operationId, db.syncDao().all().single().operationId)
        assertEquals("ACKED", db.syncDao().all().single().state)
        assertEquals(0, engine().syncNow().acknowledged)
        assertEquals(1, server.requestCount)
        assertNotNull(db.syncDao().trip(trip.id))
    }

    @Test fun manualAttemptPreservesHttpDeadlinesBlocksAndLiveLeases() = runBlocking {
        for (code in listOf(429, 503, 403, 409, 422)) {
            val trip = repo().startOrResumeTrip()
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After", "3600"))
            engine().syncNow()
            val previous = db.syncDao().all().first { it.localTripId == trip.id }
            val requests = server.requestCount
            engine().syncNow()
            assertEquals(previous, db.syncDao().operation(previous.operationId))
            assertEquals(requests, server.requestCount)
            repo().finishTrip(trip.id)
            val completed = db.syncDao().all().single { it.localTripId == trip.id && it.revision == 2 }
            db.syncDao().claim(completed.operationId, "TEST-live-lease", clock.millis() + 3_600_000)
        }
        val before = db.syncDao().all()
        val result = engine().syncNow()
        assertEquals(before, db.syncDao().all())
        assertEquals(7, result.pending) // two HTTP delays and five live leases
        assertEquals(3, result.blocked)
    }

    @Test fun finishSchedulesAfterPersistingCompletedOperation() = runBlocking {
        var calls = 0
        val scheduled = SyncScheduler { calls++ }
        val repository = RoomTripRepository(db, clock, session, scheduled)
        val trip = repository.startOrResumeTrip()
        repository.addSimulatedEvent(trip.id, DrivingEventType.SHARP_TURN)
        repository.finishTrip(trip.id)
        assertEquals(3, calls)
        assertEquals("COMPLETED", db.tripDao().findTrip(trip.id)!!.status)
        val operations = db.syncDao().all()
        assertTrue(operations.any { it.revision == 2 && it.payload.contains("COMPLETED") })
        for (op in operations.sortedBy { it.eventUuid != null }) server.enqueue(ack(op))
        assertEquals(3, engine().syncNow().acknowledged)
        assertEquals(0, engine().syncNow().pending)
    }
}
