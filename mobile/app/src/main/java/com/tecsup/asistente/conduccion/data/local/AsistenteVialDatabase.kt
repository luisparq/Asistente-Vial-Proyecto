package com.tecsup.asistente.conduccion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class, DrivingEventEntity::class, TripSyncEntity::class, EventSyncEntity::class, OutboxEntity::class], version = 3, exportSchema = true)
abstract class AsistenteVialDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "asistente_vial.db"
    }
}
