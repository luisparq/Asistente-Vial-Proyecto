package com.tecsup.asistente.conduccion.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TripEntity::class, DrivingEventEntity::class], version = 1, exportSchema = true)
abstract class AsistenteVialDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        const val NAME = "asistente_vial.db"
    }
}
