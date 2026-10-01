package com.tecsup.asistente.conduccion.data.network

import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Contract {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val utc = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
    fun timestamp(millis: Long): String = utc.format(Instant.ofEpochMilli(millis))
}

@Serializable data class TripDto(val started_at: String, val ended_at: String?, val status: String, val client_revision: Int)
@Serializable data class EventDto(val type: String, val occurred_at: String, val origin: String = "SIMULATED")
@Serializable data class LoginDto(val email: String, val password: String)
@Serializable data class TokenDto(val access_token: String, val token_type: String, val expires_at: String)
@Serializable data class UserDto(val id: Long, val name: String, val email: String)
@Serializable data class CompanyDto(val id: Long, val name: String)
@Serializable data class MeDto(val user: UserDto, val company: CompanyDto, val capabilities: List<String>)
@Serializable data class Envelope<T>(val data: T)
@Serializable data class SyncDto(val disposition: String, val acknowledged_revision: Int? = null, val current_revision: Int? = null)
@Serializable data class TripReceipt(val client_trip_id: String, val started_at: String, val ended_at: String?, val status: String, val client_revision: Int)
@Serializable data class EventReceipt(val client_event_id: String, val client_trip_id: String, val type: String, val occurred_at: String, val origin: String)
@Serializable data class Receipt<T>(val data: T, val sync: SyncDto)
