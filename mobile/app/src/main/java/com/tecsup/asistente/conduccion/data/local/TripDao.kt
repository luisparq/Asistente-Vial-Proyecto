package com.tecsup.asistente.conduccion.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TripWithEventsEntity(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId")
    val events: List<DrivingEventEntity>,
)

data class TripSummaryEntity(@Embedded val trip: TripEntity, val eventCount: Int)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActive(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun findActive(): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun findTrip(id: Long): TripEntity?

    @Query("SELECT * FROM driving_events WHERE tripId = :id")
    suspend fun events(id: Long): List<DrivingEventEntity>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeTrip(id: Long): Flow<TripWithEventsEntity?>

    @Query("""
        SELECT trips.*, (SELECT COUNT(*) FROM driving_events WHERE tripId = trips.id) AS eventCount
        FROM trips ORDER BY startedAt DESC, id DESC
    """)
    fun observeHistory(): Flow<List<TripSummaryEntity>>

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Insert
    suspend fun insertEvent(event: DrivingEventEntity): Long

    @Query("UPDATE trips SET status = 'COMPLETED', endedAt = :endedAt, activeSlot = NULL WHERE id = :id AND status = 'ACTIVE'")
    suspend fun finishTrip(id: Long, endedAt: Long): Int
}
