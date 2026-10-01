package com.tecsup.asistente.conduccion.data.network

import com.tecsup.asistente.conduccion.domain.profile.AssignedVehicle
import com.tecsup.asistente.conduccion.domain.profile.DriverDocument
import com.tecsup.asistente.conduccion.domain.profile.DriverProfile
import com.tecsup.asistente.conduccion.domain.profile.EmergencyContact
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class DriverProfileDto(
    val name: String,
    val email: String,
    val status: String,
    val access_status: String,
    val emergency_contact: EmergencyContactDto?,
    val documents: List<DriverDocumentDto>,
    val vehicle: AssignedVehicleDto?,
) {
    fun toDomain() = DriverProfile(name, email, status, access_status,
        emergency_contact?.let { EmergencyContact(it.name, it.relationship, it.phone) },
        documents.map { DriverDocument(it.id, it.type_name, it.review_status, it.expires_on?.let(LocalDate::parse), it.expired) },
        vehicle?.let { AssignedVehicle(it.plate, it.brand, it.model, it.status) })
}
@Serializable data class EmergencyContactDto(val name: String, val relationship: String, val phone: String)
@Serializable data class DriverDocumentDto(val id: Long, val type: String, val type_name: String, val review_status: String, val expires_on: String?, val expired: Boolean)
@Serializable data class AssignedVehicleDto(val plate: String, val brand: String, val model: String, val status: String)
