package com.tecsup.asistente.conduccion.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trip_sync (localTripId INTEGER NOT NULL, clientTripId TEXT NOT NULL, ownerKey TEXT, revision INTEGER NOT NULL, PRIMARY KEY(localTripId), FOREIGN KEY(localTripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_trip_sync_clientTripId ON trip_sync(clientTripId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS event_sync (localEventId INTEGER NOT NULL, clientEventId TEXT NOT NULL, PRIMARY KEY(localEventId), FOREIGN KEY(localEventId) REFERENCES driving_events(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_event_sync_clientEventId ON event_sync(clientEventId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (operationId TEXT NOT NULL, localTripId INTEGER NOT NULL, eventUuid TEXT, revision INTEGER NOT NULL, path TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, nextAttemptAt INTEGER NOT NULL, leaseUntil INTEGER NOT NULL, leaseToken TEXT, errorCode TEXT, PRIMARY KEY(operationId), FOREIGN KEY(localTripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_localTripId ON sync_outbox(localTripId)")
        db.query("SELECT id, status FROM trips").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("INSERT INTO trip_sync VALUES (?, ?, NULL, ?)", arrayOf<Any>(cursor.getLong(0), UUID.randomUUID().toString(), if (cursor.getString(1) == "ACTIVE") 1 else 2))
        }
        db.query("SELECT id FROM driving_events").use { cursor ->
            while (cursor.moveToNext()) db.execSQL("INSERT INTO event_sync VALUES (?, ?)", arrayOf<Any>(cursor.getLong(0), UUID.randomUUID().toString()))
        }
        // Historical records remain unassigned. Explicit adoption snapshots them later.
    }
}
