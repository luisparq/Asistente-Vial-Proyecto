package com.tecsup.asistente.conduccion.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trip_sync", foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["localTripId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["clientTripId"], unique = true)])
data class TripSyncEntity(@PrimaryKey val localTripId: Long, val clientTripId: String, val ownerKey: String?, val revision: Int)

@Entity(tableName = "event_sync", foreignKeys = [ForeignKey(entity = DrivingEventEntity::class, parentColumns = ["id"], childColumns = ["localEventId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["clientEventId"], unique = true)])
data class EventSyncEntity(@PrimaryKey val localEventId: Long, val clientEventId: String)

@Entity(tableName = "sync_outbox", foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["localTripId"], onDelete = ForeignKey.CASCADE)], indices = [Index("localTripId")])
data class OutboxEntity(
    @PrimaryKey val operationId: String,
    val localTripId: Long,
    val eventUuid: String?,
    val revision: Int,
    val path: String,
    val payload: String,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val leaseUntil: Long = 0,
    val leaseToken: String? = null,
    val errorCode: String? = null,
)

data class QueueStatus(val state: String, val count: Int)
