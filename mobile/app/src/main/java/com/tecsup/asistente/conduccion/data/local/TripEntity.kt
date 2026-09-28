package com.tecsup.asistente.conduccion.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips", indices = [Index(value = ["activeSlot"], unique = true)])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "ACTIVE",
    // SQLite permite varios NULL, pero solo un 1: como máximo un viaje activo.
    val activeSlot: Int? = 1,
)
