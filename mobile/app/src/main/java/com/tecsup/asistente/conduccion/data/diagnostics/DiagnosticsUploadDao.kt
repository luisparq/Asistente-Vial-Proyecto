package com.tecsup.asistente.conduccion.data.diagnostics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DiagnosticsUploadDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: DiagnosticsUploadSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSample(sample: DiagnosticsUploadSampleEntity): Long

    @Query("SELECT * FROM diagnostics_upload_session WHERE ownerKey = :owner ORDER BY startedAtMillis LIMIT 1")
    suspend fun nextSession(owner: String): DiagnosticsUploadSessionEntity?

    @Query("SELECT * FROM diagnostics_upload_sample WHERE clientSessionId = :sessionId ORDER BY sequenceNumber LIMIT :limit")
    suspend fun samples(sessionId: String, limit: Int): List<DiagnosticsUploadSampleEntity>

    @Query("DELETE FROM diagnostics_upload_sample WHERE clientSessionId = :sessionId AND sequenceNumber <= :sequence")
    suspend fun deleteThrough(sessionId: String, sequence: Long)

    @Query("SELECT COUNT(*) FROM diagnostics_upload_sample WHERE clientSessionId = :sessionId")
    suspend fun sampleCount(sessionId: String): Int

    @Query("UPDATE diagnostics_upload_session SET state = 'COMPLETING' WHERE clientSessionId = :sessionId")
    suspend fun markCompleting(sessionId: String)

    @Query("UPDATE diagnostics_upload_session SET state = 'COMPLETING' WHERE ownerKey = :owner AND state = 'ACTIVE'")
    suspend fun closeAbandoned(owner: String)

    @Query("DELETE FROM diagnostics_upload_session WHERE clientSessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
