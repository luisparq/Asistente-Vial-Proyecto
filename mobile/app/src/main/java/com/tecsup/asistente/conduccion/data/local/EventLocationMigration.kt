package com.tecsup.asistente.conduccion.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE driving_events ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE driving_events ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE driving_events ADD COLUMN locationOrigin TEXT")
        db.execSQL("ALTER TABLE driving_events ADD COLUMN gpsUtcSeconds INTEGER")
        db.execSQL("ALTER TABLE driving_events ADD COLUMN gpsReceivedAt INTEGER")
        db.execSQL("ALTER TABLE driving_events ADD COLUMN gpsAgeMillis INTEGER")
    }
}
