package com.tecsup.asistente.conduccion.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "driving_events",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["tripId"])],
)
data class DrivingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val type: String,
    val occurredAt: Long,
    val origin: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationOrigin: String? = null,
    val gpsUtcSeconds: Int? = null,
    val gpsReceivedAt: Long? = null,
    val gpsAgeMillis: Long? = null,
)
