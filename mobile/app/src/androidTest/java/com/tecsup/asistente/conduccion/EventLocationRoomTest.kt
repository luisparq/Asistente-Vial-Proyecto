package com.tecsup.asistente.conduccion

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.local.*
import com.tecsup.asistente.conduccion.data.network.*
import com.tecsup.asistente.conduccion.data.ble.RawSensorDataSource
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.data.sync.SyncEngine
import com.tecsup.asistente.conduccion.domain.model.*
import com.tecsup.asistente.conduccion.domain.sync.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.time.Clock
import java.util.UUID
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

/** TEST databases and synthetic coordinates only. No application/user data is removed. */
class EventLocationRoomTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun migrationPreservesLegacyEventsUuidAndPendingPayload() = runBlocking {
        val name="TEST-location-migration-${UUID.randomUUID()}.db"
        val payload="""{"type":"SHARP_TURN","occurred_at":"1970-01-01T00:00:01.000Z","origin":"SIMULATED"}"""
        val path=context.getDatabasePath(name); path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { sql ->
            sql.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `status` TEXT NOT NULL, `activeSlot` INTEGER)")
            sql.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_activeSlot` ON `trips` (`activeSlot`)")
            sql.execSQL("CREATE TABLE IF NOT EXISTS `driving_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `tripId` INTEGER NOT NULL, `type` TEXT NOT NULL, `occurredAt` INTEGER NOT NULL, `origin` TEXT NOT NULL, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sql.execSQL("CREATE INDEX IF NOT EXISTS `index_driving_events_tripId` ON `driving_events` (`tripId`)")
            sql.execSQL("CREATE TABLE IF NOT EXISTS `trip_sync` (`localTripId` INTEGER NOT NULL, `clientTripId` TEXT NOT NULL, `ownerKey` TEXT, `revision` INTEGER NOT NULL, PRIMARY KEY(`localTripId`), FOREIGN KEY(`localTripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sql.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trip_sync_clientTripId` ON `trip_sync` (`clientTripId`)")
            sql.execSQL("CREATE TABLE IF NOT EXISTS `event_sync` (`localEventId` INTEGER NOT NULL, `clientEventId` TEXT NOT NULL, PRIMARY KEY(`localEventId`), FOREIGN KEY(`localEventId`) REFERENCES `driving_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sql.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_sync_clientEventId` ON `event_sync` (`clientEventId`)")
            sql.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`operationId` TEXT NOT NULL, `localTripId` INTEGER NOT NULL, `eventUuid` TEXT, `revision` INTEGER NOT NULL, `path` TEXT NOT NULL, `payload` TEXT NOT NULL, `state` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `nextAttemptAt` INTEGER NOT NULL, `leaseUntil` INTEGER NOT NULL, `leaseToken` TEXT, `errorCode` TEXT, PRIMARY KEY(`operationId`), FOREIGN KEY(`localTripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            sql.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_localTripId` ON `sync_outbox` (`localTripId`)")
            sql.execSQL("INSERT INTO trips VALUES (1,0,NULL,'ACTIVE',1)")
            sql.execSQL("INSERT INTO driving_events VALUES (1,1,'SHARP_TURN',1000,'SIMULATED')")
            sql.execSQL("INSERT INTO trip_sync VALUES (1,'TEST-trip',NULL,1)")
            sql.execSQL("INSERT INTO event_sync VALUES (1,'TEST-event')")
            sql.execSQL("INSERT INTO sync_outbox VALUES ('TEST-op',1,'TEST-event',0,'TEST-path',?,'PENDING',0,0,0,NULL,NULL)",arrayOf(payload))
            sql.version=2
        }
        val db=Room.databaseBuilder(context,AsistenteVialDatabase::class.java,name).addMigrations(MIGRATION_2_3).build()
        try {
            val event=db.tripDao().events(1).single()
            assertEquals(1000L,event.occurredAt); assertNull(event.latitude); assertNull(event.gpsReceivedAt)
            assertEquals("TEST-event",db.syncDao().event(1)!!.clientEventId)
            assertEquals(payload,db.syncDao().all().single().payload)
            assertEquals("PENDING",db.syncDao().all().single().state)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun eventLocationPersistsOfflineAndSynchronizesWithMatchingReceipt() = runBlocking {
        val name="TEST-location-sync-${UUID.randomUUID()}.db"
        val db=Room.databaseBuilder(context,AsistenteVialDatabase::class.java,name).build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val source=RawSensorDataSource(scope)
        val server=MockWebServer().apply { start() }
        val session=MemorySessionStore().apply { save(Session(server.url("/").toString(),1,1,"TEST","test@example.invalid","TEST-token")) }
        val clock=Clock.systemUTC()
        try {
            val repo=RoomTripRepository(db,clock,session,SyncScheduler {},source)
            val trip=repo.startOrResumeTrip()
            val frame=java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put(1).put(2).put(3).put(7).putInt(-160000000).putInt(-710000000).putInt(43200).putInt(100).array()
            source.acceptGps(frame)
            val event=repo.addSimulatedEvent(trip.id,DrivingEventType.SHARP_TURN)
            assertEquals(LocationOrigin.GPS_NEO6M,event.locationOrigin)
            assertEquals(-16.0,event.latitude!!,0.0)
            val op=db.syncDao().all().single { it.eventUuid != null }
            assertEquals(-16.0,Contract.json.decodeFromString<EventDto>(op.payload).latitude!!,0.0)
            // Queue snapshots are persisted before any HTTP call.
            assertEquals("PENDING",op.state)
            for (pending in db.syncDao().all().sortedBy { it.eventUuid != null }) {
                val meta=db.syncDao().trip(trip.id)!!
                val data=Contract.json.parseToJsonElement(pending.payload).jsonObject
                server.enqueue(MockResponse().setResponseCode(200).setBody(buildJsonObject {
                    putJsonObject("data") { data.forEach { (k,v)->put(k,v) }; put("client_trip_id",meta.clientTripId)
                        pending.eventUuid?.let { put("client_event_id",it) } }
                    putJsonObject("sync") { put("disposition","already_applied")
                        if (pending.eventUuid==null) { put("acknowledged_revision",pending.revision); put("current_revision",pending.revision) } }
                }.toString()))
            }
            assertFalse(SyncEngine(db,session,LaravelApi(),clock).drain())
            assertTrue(db.syncDao().all().all { it.state=="ACKED" })
            assertEquals(2,server.requestCount)
            server.takeRequest()
            assertEquals(op.payload,server.takeRequest().body.readUtf8())
            source.reset(com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus.DISCONNECTED)
            val absent=repo.addSimulatedEvent(trip.id,DrivingEventType.HARSH_BRAKING)
            assertNull(absent.latitude)
        } finally { scope.cancel(); db.close(); server.shutdown(); context.deleteDatabase(name) }
    }
}
