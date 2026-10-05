package com.tecsup.asistente.conduccion.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.data.local.AsistenteVialDatabase
import com.tecsup.asistente.conduccion.data.sync.SyncAccount
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.profile.DriverProfileRepository
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccessState(val busy: Boolean = false, val message: String? = null, val destination: String? = null)

@HiltViewModel
class AccessViewModel @Inject constructor(
    private val account: SyncAccount,
    store: SessionStore,
    private val trips: TripRepository,
    profile: DriverProfileRepository,
    ble: BleRepository,
    database: AsistenteVialDatabase,
    val configuration: ServerConfiguration,
) : ViewModel() {
    val session = store.state
    val connection = ble.connection
    val profile = profile.observe().stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(0, 0), ProfileState.Loading)
    // Presentation only: anonymous demo records are not shown as a driver's own history.
    val demoTripIds = database.syncDao().unassigned().map { rows -> rows.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val mutableState = MutableStateFlow(AccessState())
    val state = mutableState.asStateFlow()

    private fun perform(action: suspend () -> String) {
        if (state.value.busy || state.value.destination != null) return
        mutableState.value = AccessState(busy = true)
        viewModelScope.launch {
            try { mutableState.value = AccessState(destination = action()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val message = when {
                    error.message?.contains("HTTP 401") == true -> "El correo o la contraseña no son correctos."
                    error.message?.contains("HTTP 403") == true -> "Tu cuenta no tiene acceso. Contacta al administrador de tu empresa."
                    error.message?.contains("HTTP 429") == true -> "Demasiados intentos. Espera un momento antes de volver a iniciar sesión."
                    error is IllegalArgumentException -> "Revisa la configuración del servidor en Ajustes."
                    error is IllegalStateException && error.message?.contains("HTTP") != true -> error.message
                    else -> "No se pudo conectar. Comprueba la conexión e inténtalo de nuevo."
                }
                mutableState.value = AccessState(message = message)
            }
        }
    }

    fun login(email: String, password: String) = perform {
        check(session.value != null || trips.observeActiveTrip().first() == null) {
            "Finaliza el recorrido de demostración antes de iniciar sesión con tu cuenta."
        }
        account.login(configuration.baseUrl, email.trim(), password)
        "welcome"
    }

    fun demo() = perform {
        // Logout uses the existing active-trip guard. Demo never inherits a real account's owner/token.
        if (session.value != null) account.logout()
        "home"
    }

    fun continueSession() { if (session.value != null && !state.value.busy) mutableState.value = AccessState(destination = "welcome") }
    fun navigationHandled() { mutableState.value = AccessState() }
}
