package com.tecsup.asistente.conduccion.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS diagnostics_upload_session (clientSessionId TEXT NOT NULL, ownerKey TEXT NOT NULL, deviceCode TEXT NOT NULL, startedAtMillis INTEGER NOT NULL, state TEXT NOT NULL, PRIMARY KEY(clientSessionId))")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostics_upload_session_ownerKey ON diagnostics_upload_session(ownerKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostics_upload_session_state ON diagnostics_upload_session(state)")
        db.execSQL("CREATE TABLE IF NOT EXISTS diagnostics_upload_sample (clientSessionId TEXT NOT NULL, sequenceNumber INTEGER NOT NULL, sampledAtMillis INTEGER NOT NULL, axRaw INTEGER NOT NULL, ayRaw INTEGER NOT NULL, azRaw INTEGER NOT NULL, gxRaw INTEGER NOT NULL, gyRaw INTEGER NOT NULL, gzRaw INTEGER NOT NULL, PRIMARY KEY(clientSessionId, sequenceNumber), FOREIGN KEY(clientSessionId) REFERENCES diagnostics_upload_session(clientSessionId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diagnostics_upload_sample_clientSessionId ON diagnostics_upload_sample(clientSessionId)")
    }
}
