package com.tecsup.asistente.conduccion.data.network

import com.tecsup.asistente.conduccion.domain.access.AccountActivation
import com.tecsup.asistente.conduccion.domain.access.ActivationFailure
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject

@Serializable
private data class ActivationDto(val activation_code: String, val password: String, val password_confirmation: String) {
    override fun toString() = "ActivationDto(redacted)"
}

class LaravelAccountActivation @Inject constructor(private val api: LaravelApi) : AccountActivation {
    override suspend fun activate(baseUrl: String, code: String, password: String, confirmation: String) {
        val reply = try {
            api.request(baseUrl, "api/v1/auth/activate", payload = Contract.json.encodeToString(ActivationDto(code, password, confirmation)), method = "POST")
        } catch (_: IOException) {
            throw ActivationFailure("No se pudo confirmar la activación. Comprueba la conexión. Si ya se activó, intenta iniciar sesión.")
        }
        if (reply.code == 204) return
        val fields = runCatching { Contract.json.parseToJsonElement(reply.body).jsonObject["error"]?.jsonObject?.get("fields")?.jsonObject }.getOrNull()
        throw ActivationFailure(when (reply.code) {
            422 -> if (fields?.containsKey("password") == true) "La contraseña debe tener entre 12 y 255 caracteres y coincidir con la confirmación."
                else "Código no válido, vencido, utilizado o no disponible para activar este conductor. Solicita uno nuevo a tu empresa."
            429 -> "Demasiados intentos. Espera antes de volver a intentar."
            403 -> "La cuenta no está disponible para activación. Consulta con tu empresa."
            else -> "No se pudo activar la cuenta. Inténtalo nuevamente más tarde."
        })
    }
}
