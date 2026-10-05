package com.tecsup.asistente.conduccion.domain.access

fun interface AccountActivation {
    suspend fun activate(baseUrl: String, code: String, password: String, confirmation: String)
}

class ActivationFailure(message: String) : Exception(message)
