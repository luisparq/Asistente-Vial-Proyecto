package com.tecsup.asistente.conduccion.domain.profile

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

data class DriverProfile(
    val name: String,
    val email: String,
    val status: String,
    val accessStatus: String,
    val emergencyContact: EmergencyContact?,
    val documents: List<DriverDocument>,
    val vehicle: AssignedVehicle?,
)
data class EmergencyContact(val name: String, val relationship: String, val phone: String)
data class DriverDocument(val id: Long, val typeName: String, val reviewStatus: String, val expiresOn: LocalDate?, val expired: Boolean)
data class AssignedVehicle(val plate: String, val brand: String, val model: String, val status: String)

sealed interface ProfileState {
    data object Loading : ProfileState
    data object SignedOut : ProfileState
    data object NotRegistered : ProfileState
    data object Offline : ProfileState
    data object SessionExpired : ProfileState
    data object Forbidden : ProfileState
    data object ServerError : ProfileState
    data class Ready(val profile: DriverProfile) : ProfileState
}

interface DriverProfileRepository {
    fun observe(): Flow<ProfileState>
    fun refresh()
}
