package com.tecsup.asistente.conduccion

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.sync.*
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SyncRestartPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture get() = context.getSharedPreferences("sync-restart-TEST-expectations", 0)
    @Test fun aPrepareQueue() = runBlocking {
        val name = "sync-restart-TEST-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, name).build()
        try {
            val session = MemorySessionStore().apply { save(Session("https://test.invalid/", 1, 1, "TEST ONLY", "test@example.invalid", "TEST-only-token")) }
            val repo = RoomTripRepository(db, Clock.systemUTC(), session, SyncScheduler {})
            val trip = repo.startOrResumeTrip()
            repo.addSimulatedEvent(trip.id, DrivingEventType.SHARP_TURN)
            repo.finishTrip(trip.id)
            val snapshot = JSONArray()
            db.syncDao().all().forEach { snapshot.put(JSONObject().put("id", it.operationId).put("payload", it.payload)) }
            db.syncDao().claim(db.syncDao().all().first().operationId, "TEST-abandoned", 0)
            assertTrue(fixture.edit().putString("database", name).putString("snapshot", snapshot.toString()).putString("uuid", db.syncDao().trip(trip.id)!!.clientTripId).putLong("trip", trip.id).commit())
        } finally { db.close() }
    }
    @Test fun bRecoverQueueAfterProcessRestart() = runBlocking {
        val name = checkNotNull(fixture.getString("database", null))
        val db = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, name).build()
        try {
            val actual = JSONArray()
            db.syncDao().all().forEach { actual.put(JSONObject().put("id", it.operationId).put("payload", it.payload)) }
            assertEquals(fixture.getString("snapshot", null), actual.toString())
            assertEquals(fixture.getString("uuid", null), db.syncDao().trip(fixture.getLong("trip", -1))!!.clientTripId)
            assertTrue(db.syncDao().all().any { it.state == "IN_FLIGHT" })
            db.syncDao().recover(0)
            assertFalse(db.syncDao().all().any { it.state == "IN_FLIGHT" })
            assertEquals(3, db.syncDao().all().size)
        } finally { db.close(); context.deleteDatabase(name); fixture.edit().clear().commit() }
    }
}
