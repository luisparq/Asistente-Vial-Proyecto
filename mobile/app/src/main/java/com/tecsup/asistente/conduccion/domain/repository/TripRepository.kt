package com.tecsup.asistente.conduccion.domain.repository

import com.tecsup.asistente.conduccion.domain.model.DrivingEvent
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.Trip
import com.tecsup.asistente.conduccion.domain.model.TripSummary
import com.tecsup.asistente.conduccion.domain.model.TripWithEvents
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeActiveTrip(): Flow<Trip?>
    fun observeHistory(): Flow<List<TripSummary>>
    fun observeTrip(id: Long): Flow<TripWithEvents?>
    /** Atómico e idempotente: recupera el activo o crea uno nuevo. */
    suspend fun startOrResumeTrip(): Trip
    /** Solo acepta eventos de prueba y solo sobre un viaje activo. */
    suspend fun addSimulatedEvent(tripId: Long, type: DrivingEventType): DrivingEvent
    /** Finalizar de nuevo no modifica la fecha de cierre original. */
    suspend fun finishTrip(tripId: Long)
}
