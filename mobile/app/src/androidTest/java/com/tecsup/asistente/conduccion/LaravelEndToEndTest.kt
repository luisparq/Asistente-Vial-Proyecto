package com.tecsup.asistente.conduccion

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.data.sync.*
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.sync.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.tecsup.asistente.conduccion.data.repository.LaravelDriverProfileRepository
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in REAL HTTP to isolated Laravel TEST database; not run against a user's server. */
class LaravelEndToEndTest {
    @Test fun androidRoomToLaravelWithReplayAndReauthentication(): Unit = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Run scripts/verify-laravel-e2e.ps1 for the real server test", args.getString("laravelE2E") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "laravel-e2e-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        var db = open()
        val session = MemorySessionStore()
        val scheduler = SyncScheduler {}
        val api = LaravelApi()
        val base = checkNotNull(args.getString("baseUrl"))
        val email = checkNotNull(args.getString("testEmail"))
        val password = checkNotNull(args.getString("testPassword"))
        val clock = Clock.systemUTC()
        try {
            SyncAccount(db, session, api, scheduler).login(base, email, password)
            val profile = LaravelDriverProfileRepository(api, session).observe().first { it != ProfileState.Loading }
            assertTrue(profile is ProfileState.Ready)
            assertEquals("TEST ONLY Android profile", (profile as ProfileState.Ready).profile.name)
            assertEquals("TEST-HTTP", profile.profile.vehicle?.plate)
            assertEquals("TEST ONLY emergency", profile.profile.emergencyContact?.name)
            assertTrue(profile.profile.documents.isEmpty())
            val repo = RoomTripRepository(db, clock, session, scheduler)
            val trip = repo.startOrResumeTrip()
            val uuid = db.syncDao().trip(trip.id)!!.clientTripId
            val path = "api/v1/trips/$uuid"
            SyncEngine(db, session, api, clock).syncNow()
            val activeReply = api.request(base, path, session.state.value!!.token, method = "GET")
            assertEquals(200, activeReply.code)
            assertEquals("ACTIVE", Contract.json.parseToJsonElement(activeReply.body).jsonObject["data"]!!.jsonObject["status"]!!.jsonPrimitive.content)
            repo.addSimulatedEvent(trip.id, DrivingEventType.HARSH_BRAKING)
            repo.finishTrip(trip.id)
            SyncEngine(db, session, api, clock).syncNow()
            assertTrue(db.syncDao().all().all { it.state == "ACKED" })
            db.close(); db = open()
            assertEquals(uuid, db.syncDao().trip(trip.id)!!.clientTripId)
            // Lost-ACK replay: identical persistent IDs/payload, already stored remotely.
            db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET state = 'PENDING'")
            SyncEngine(db, session, api, clock).syncNow()
            assertTrue(db.syncDao().all().all { it.state == "ACKED" })
            val reply = api.request(base, path, session.state.value!!.token, method = "GET")
            val received = Contract.json.parseToJsonElement(reply.body).jsonObject["data"]!!.jsonObject
            assertEquals("COMPLETED", received["status"]!!.jsonPrimitive.content)
            assertEquals(1, received["received_event_count"]!!.jsonPrimitive.int)
            val events = api.request(base, "$path/events", session.state.value!!.token, method = "GET")
            assertEquals("SIMULATED", Contract.json.parseToJsonElement(events.body).jsonObject["data"]!!.jsonArray.single().jsonObject["origin"]!!.jsonPrimitive.content)
            api.request(base, "api/v1/auth/token", session.state.value!!.token, method = "DELETE")
            db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET state = 'PENDING' WHERE eventUuid IS NULL AND revision = 1")
            SyncEngine(db, session, api, clock).syncNow()
            assertNull(session.state.value!!.token)
            assertTrue(db.syncDao().all().any { it.state == "BLOCKED_AUTH" })
            SyncAccount(db, session, api, scheduler).login(base, email, password)
            SyncEngine(db, session, api, clock).syncNow()
            assertTrue(db.syncDao().all().all { it.state == "ACKED" })
            api.request(base, "api/v1/auth/token", session.state.value!!.token, method = "DELETE")
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
