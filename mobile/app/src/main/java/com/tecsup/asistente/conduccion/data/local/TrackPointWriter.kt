package com.tecsup.asistente.conduccion.data.local

import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.network.TrackPointDto
import com.tecsup.asistente.conduccion.domain.model.EventLocation
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.encodeToString

object TrackPointWriter {
    const val REVISION = -1
    private const val SAMPLE_INTERVAL_MS = 5_000L

    // Called inside the same transaction that checks whether the trip is active.
    suspend fun record(
        db: AsistenteVialDatabase, trip: TripEntity, fix: EventLocation?, force: Boolean = false,
    ): Boolean {
        if (fix == null || fix.receivedAtMillis < trip.startedAt) return false
        val dao = db.syncDao()
        val meta = dao.trip(trip.id) ?: return false
        val capturedAt = Contract.timestamp(fix.receivedAtMillis)
        val previous = dao.lastTrackPoint(trip.id, REVISION)?.let {
            Instant.parse(Contract.json.decodeFromString<TrackPointDto>(it.payload).captured_at).toEpochMilli()
        }
        if (previous != null && (fix.receivedAtMillis <= previous ||
                (!force && fix.receivedAtMillis - previous < SAMPLE_INTERVAL_MS))) return false

        val id = UUID.randomUUID().toString()
        val point = TrackPointDto(capturedAt, fix.latitude, fix.longitude)
        dao.enqueue(OutboxEntity(id, trip.id, null, REVISION,
            "api/v1/trips/${meta.clientTripId}/track-points/$id", Contract.json.encodeToString(point)))
        return true
    }
}
