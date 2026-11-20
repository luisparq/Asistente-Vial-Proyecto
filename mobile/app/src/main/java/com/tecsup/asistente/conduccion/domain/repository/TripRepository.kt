package com.tecsup.asistente.conduccion.domain.repository

import com.tecsup.asistente.conduccion.domain.model.DrivingEvent
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.Trip
import com.tecsup.asistente.conduccion.domain.model.TripSummary
import com.tecsup.asistente.conduccion.domain.model.TripWithEvents
import kotlinx.coroutines.flow.Flow

/** Resultado de iniciar un recorrido: [created] distingue uno nuevo de uno retomado. */
data class StartedTrip(val trip: Trip, val created: Boolean)

interface TripRepository {
    fun observeActiveTrip(): Flow<Trip?>
    fun observeConfirmedManeuverAlert(tripId: Long): Flow<String?> = kotlinx.coroutines.flow.flowOf(null)
    fun observeHistory(): Flow<List<TripSummary>>
    fun observeTrip(id: Long): Flow<TripWithEvents?>
    /** Atómico e idempotente: recupera el activo o crea uno nuevo e indica cuál ocurrió. */
    suspend fun startOrResume(): StartedTrip
    /** Atajo de [startOrResume] cuando no se necesita saber si el recorrido fue creado. */
    suspend fun startOrResumeTrip(): Trip = startOrResume().trip
    /** Solo acepta eventos de prueba y solo sobre un viaje activo. */
    suspend fun addSimulatedEvent(tripId: Long, type: DrivingEventType): DrivingEvent
    /** Genera una maniobra de prueba y devuelve el ID de la alerta recién registrada, si corresponde. */
    suspend fun simulateManeuver(tripId: Long, type: DrivingEventType): String?

    /** Finalizar de nuevo no modifica la fecha de cierre original. */
    suspend fun finishTrip(tripId: Long)
}
