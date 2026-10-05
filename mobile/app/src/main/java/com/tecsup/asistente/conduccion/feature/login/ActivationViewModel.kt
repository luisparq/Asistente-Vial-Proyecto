package com.tecsup.asistente.conduccion.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.BuildConfig
import com.tecsup.asistente.conduccion.domain.access.AccountActivation
import com.tecsup.asistente.conduccion.domain.access.ActivationFailure
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivationUiState(val busy: Boolean = false, val activated: Boolean = false, val message: String? = null)

@HiltViewModel
class ActivationViewModel @Inject constructor(private val activation: AccountActivation, store: SessionStore) : ViewModel() {
    val initialBaseUrl = store.state.value?.baseUrl ?: BuildConfig.API_BASE_URL
    private val mutableState = MutableStateFlow(ActivationUiState())
    val state = mutableState.asStateFlow()

    fun activate(baseUrl: String, code: String, password: String, confirmation: String) {
        if (state.value.busy || state.value.activated) return
        val problem = when {
            baseUrl.isBlank() -> "Completa la configuración en Ajustes antes de activar tu cuenta."
            !code.trim().matches(Regex("[a-f0-9]{64}")) -> "Introduce el código completo entregado por tu empresa."
            password.length !in 12..255 -> "La contraseña debe tener entre 12 y 255 caracteres."
            password != confirmation -> "Las contraseñas no coinciden."
            else -> null
        }
        if (problem != null) { mutableState.value = ActivationUiState(message = problem); return }
        mutableState.value = ActivationUiState(busy = true)
        viewModelScope.launch {
            try {
                activation.activate(baseUrl, code.trim(), password, confirmation)
                mutableState.value = ActivationUiState(activated = true, message = "Cuenta activada. Inicia sesión con tu correo y la nueva contraseña.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: ActivationFailure) { mutableState.value = ActivationUiState(message = error.message) }
            catch (_: IllegalArgumentException) { mutableState.value = ActivationUiState(message = "Revisa la configuración del servidor en Ajustes.") }
            catch (_: Exception) { mutableState.value = ActivationUiState(message = "No se pudo activar la cuenta. Inténtalo nuevamente.") }
        }
    }
}
