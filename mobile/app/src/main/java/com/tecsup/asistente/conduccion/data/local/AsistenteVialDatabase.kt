package com.tecsup.asistente.conduccion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class, DrivingEventEntity::class, TripSyncEntity::class, EventSyncEntity::class, OutboxEntity::class,
    com.tecsup.asistente.conduccion.data.diagnostics.DiagnosticsUploadSessionEntity::class,
    com.tecsup.asistente.conduccion.data.diagnostics.DiagnosticsUploadSampleEntity::class, ManeuverAlertEntity::class, com.tecsup.asistente.conduccion.data.diagnostics.SensorRelayRow::class], version = 6, exportSchema = true)
abstract class AsistenteVialDatabase : RoomDatabase() {
    abstract fun sensorRelayDao(): com.tecsup.asistente.conduccion.data.diagnostics.SensorRelayDao
    abstract fun tripDao(): TripDao
    abstract fun syncDao(): SyncDao
    abstract fun diagnosticsUploadDao(): com.tecsup.asistente.conduccion.data.diagnostics.DiagnosticsUploadDao

    companion object {
        const val NAME = "asistente_vial.db"
    }
}
