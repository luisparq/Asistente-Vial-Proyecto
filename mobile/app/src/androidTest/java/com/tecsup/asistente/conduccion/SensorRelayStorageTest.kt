package com.tecsup.asistente.conduccion

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.diagnostics.MIGRATION_5_6
import com.tecsup.asistente.conduccion.data.diagnostics.SensorRelayRow
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.local.TripEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Synthetic packets in an isolated database; no physical BLE or network claims. */
class SensorRelayStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "sensor-relay-test-${System.nanoTime()}.db"
    private var database: AsistenteVialDatabase? = null

    private fun open() = Room.databaseBuilder(context, AsistenteVialDatabase::class.java, name)
        .addMigrations(MIGRATION_5_6).build().also { database = it }

    private fun close() { database?.close(); database = null }

    @After fun cleanup() {
        close()
        context.deleteDatabase(name) // Exclusively this test's generated database.
    }

    @Test fun pendingPacketsSurviveReopenAndAcknowledgementsAreScopedToOwner() = runBlocking {
        var db = open()
        val original = SensorRelayRow("owner-a|ONE|boot|1", "owner-a", "ORIGINAL_TEST_PACKET", 1000)
        db.sensorRelayDao().insert(original)
        db.sensorRelayDao().insert(original.copy(payload = "CONFLICTING_TEST_PACKET"))
        db.sensorRelayDao().insert(SensorRelayRow("owner-a|ONE|boot|2", "owner-a", "SECOND_TEST_PACKET", 1250))
        val foreign = SensorRelayRow("owner-b|ONE|boot|1", "owner-b", "OTHER_OWNER_TEST_PACKET", 1000)
        db.sensorRelayDao().insert(foreign)
        close()

        db = open()
        assertEquals(2, db.sensorRelayDao().count("owner-a"))
        assertEquals(original, db.sensorRelayDao().batch("owner-a").first())
        db.sensorRelayDao().acknowledge("owner-a", listOf(foreign.id))
        assertEquals(1, db.sensorRelayDao().count("owner-b"))
        db.sensorRelayDao().acknowledge("owner-a", listOf(original.id))
        assertEquals(listOf("owner-a|ONE|boot|2"), db.sensorRelayDao().batch("owner-a").map { it.id })
        assertEquals(foreign, db.sensorRelayDao().batch("owner-b").single())
    }

    @Test fun migrationAddsRelayQueueAndPreservesExistingTrips() = runBlocking {
        val initial = open()
        val tripId = initial.tripDao().insertTrip(TripEntity(startedAt = 1000, endedAt = 2000, status = "COMPLETED", activeSlot = null))
        // Versions 5 and 6 differ only in this new queue. Recreate that historical
        // shape without copying Room's generated schema into this test.
        close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP TABLE sensor_relay_outbox")
            it.version = 5
        }

        val migrated = open()
        assertEquals(1000L, migrated.tripDao().findTrip(tripId)!!.startedAt)
        assertEquals("COMPLETED", migrated.tripDao().findTrip(tripId)!!.status)
        assertEquals(0, migrated.sensorRelayDao().count("TEST_OWNER"))
        migrated.sensorRelayDao().insert(SensorRelayRow("TEST_ID", "TEST_OWNER", "TEST_PACKET", 3000))
        assertEquals(1, migrated.sensorRelayDao().count("TEST_OWNER"))
    }
}
