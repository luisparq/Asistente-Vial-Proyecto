package com.tecsup.asistente.conduccion.data.local

import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.network.EventDto
import com.tecsup.asistente.conduccion.data.network.TripDto
import kotlinx.serialization.encodeToString

object OutboxWriter {
    suspend fun trip(db: AsistenteVialDatabase, trip: TripEntity, meta: TripSyncEntity) {
        val dto = TripDto(Contract.timestamp(trip.startedAt), trip.endedAt?.let(Contract::timestamp), trip.status, meta.revision)
        db.syncDao().enqueue(OutboxEntity("${meta.clientTripId}:${meta.revision}", trip.id, null, meta.revision,
            "api/v1/trips/${meta.clientTripId}", Contract.json.encodeToString(dto)))
    }
    suspend fun event(db: AsistenteVialDatabase, event: DrivingEventEntity, tripUuid: String, uuid: String) {
        check(event.origin == "SIMULATED") { "Solo se sincronizan eventos SIMULATED." }
        val dto = EventDto(event.type, Contract.timestamp(event.occurredAt), event.origin,
            event.latitude, event.longitude, event.locationOrigin, event.gpsUtcSeconds,
            event.gpsReceivedAt?.let(Contract::timestamp), event.gpsAgeMillis)
        db.syncDao().enqueue(OutboxEntity(uuid, event.tripId, uuid, 0,
            "api/v1/trips/$tripUuid/events/$uuid", Contract.json.encodeToString(dto)))
    }
}
