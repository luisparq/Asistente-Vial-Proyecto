package com.tecsup.asistente.conduccion.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Insert suspend fun insertTrip(meta: TripSyncEntity)
    @Insert suspend fun insertEvent(meta: EventSyncEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun enqueue(operation: OutboxEntity)
    @Query("SELECT * FROM trip_sync WHERE localTripId = :id") suspend fun trip(id: Long): TripSyncEntity?
    @Query("SELECT * FROM event_sync WHERE localEventId = :id") suspend fun event(id: Long): EventSyncEntity?
    @Query("UPDATE trip_sync SET revision = :revision WHERE localTripId = :id") suspend fun revision(id: Long, revision: Int)
    @Query("UPDATE trip_sync SET ownerKey = :owner WHERE localTripId = :id AND ownerKey IS NULL") suspend fun adopt(id: Long, owner: String): Int
    @Query("SELECT trips.* FROM trips JOIN trip_sync ON trips.id = trip_sync.localTripId WHERE ownerKey IS NULL AND status = 'COMPLETED' ORDER BY startedAt DESC")
    fun unassigned(): Flow<List<TripEntity>>
    @Query("SELECT sync_outbox.state, COUNT(*) AS count FROM sync_outbox JOIN trip_sync ON trip_sync.localTripId = sync_outbox.localTripId WHERE ownerKey = :owner GROUP BY sync_outbox.state")
    fun statuses(owner: String?): Flow<List<QueueStatus>>
    @Query("SELECT * FROM sync_outbox ORDER BY localTripId, revision, operationId") suspend fun all(): List<OutboxEntity>
    @Query("SELECT * FROM sync_outbox WHERE operationId = :id") suspend fun operation(id: String): OutboxEntity?
    @Query("UPDATE sync_outbox SET state = 'RETRY_WAIT', leaseToken = NULL, leaseUntil = 0 WHERE state = 'IN_FLIGHT' AND leaseUntil <= :now")
    suspend fun recover(now: Long)
    @Query("""SELECT o.* FROM sync_outbox o JOIN trip_sync m ON m.localTripId = o.localTripId
        WHERE m.ownerKey = :owner AND o.state IN ('PENDING','RETRY_WAIT') AND o.nextAttemptAt <= :now
        AND (o.eventUuid IS NULL OR EXISTS (SELECT 1 FROM sync_outbox p WHERE p.localTripId = o.localTripId AND p.eventUuid IS NULL AND p.state = 'ACKED'))
        ORDER BY o.eventUuid IS NOT NULL, o.revision, o.localTripId LIMIT 1""")
    suspend fun next(owner: String, now: Long): OutboxEntity?
    @Query("UPDATE sync_outbox SET state = 'IN_FLIGHT', leaseUntil = :until, leaseToken = :lease, attempts = attempts + 1 WHERE operationId = :id AND state IN ('PENDING','RETRY_WAIT')")
    suspend fun claim(id: String, lease: String, until: Long): Int
    @Query("UPDATE sync_outbox SET state = :state, errorCode = :error, nextAttemptAt = :nextAt, leaseUntil = 0, leaseToken = NULL WHERE operationId = :id AND leaseToken = :lease AND state = 'IN_FLIGHT'")
    suspend fun complete(id: String, lease: String, state: String, error: String?, nextAt: Long): Int
    @Query("UPDATE sync_outbox SET state = 'PENDING', errorCode = NULL, nextAttemptAt = 0 WHERE state = 'BLOCKED_AUTH' AND localTripId IN (SELECT localTripId FROM trip_sync WHERE ownerKey = :owner)")
    suspend fun resumeAuth(owner: String)
    // Explicit manual retry only: never release HTTP Retry-After, blocked rows or live leases.
    @Query("UPDATE sync_outbox SET nextAttemptAt = 0 WHERE state = 'RETRY_WAIT' AND errorCode = 'network_unavailable' AND localTripId IN (SELECT localTripId FROM trip_sync WHERE ownerKey = :owner)")
    suspend fun advanceNetworkRetries(owner: String)
    @Query("SELECT COUNT(*) FROM sync_outbox o JOIN trip_sync m ON m.localTripId = o.localTripId WHERE ownerKey = :owner AND o.state IN ('PENDING','RETRY_WAIT','IN_FLIGHT')")
    suspend fun pending(owner: String): Int
    @Query("SELECT trips.* FROM trips JOIN trip_sync m ON m.localTripId = trips.id WHERE status = 'ACTIVE' AND (m.ownerKey IS NULL OR m.ownerKey = :owner) LIMIT 1")
    fun active(owner: String?): Flow<TripEntity?>
    @Query("SELECT trips.*, (SELECT COUNT(*) FROM driving_events WHERE tripId = trips.id) AS eventCount FROM trips JOIN trip_sync m ON m.localTripId = trips.id WHERE m.ownerKey IS NULL OR m.ownerKey = :owner ORDER BY startedAt DESC, id DESC")
    fun history(owner: String?): Flow<List<TripSummaryEntity>>
}
