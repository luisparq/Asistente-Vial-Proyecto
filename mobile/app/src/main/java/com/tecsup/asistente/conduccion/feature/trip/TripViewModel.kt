package com.tecsup.asistente.conduccion.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.asLoadState
import com.tecsup.asistente.conduccion.domain.model.DataOrigin
import com.tecsup.asistente.conduccion.domain.model.TripWithEvents
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripActionState(
    val busy: Boolean = false,
    val failed: Boolean = false,
    val openHistory: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripViewModel @Inject constructor(private val repository: TripRepository) : ViewModel() {
    val trip = repository.observeActiveTrip()
        .flatMapLatest { active ->
            active?.let { repository.observeTrip(it.id) } ?: flowOf(null)
        }
        // Los registros se conservan; solo se ocultan en la pantalla activa los eventos
        // marcados como simulados para no mezclarlos con datos del recorrido en curso.
        .map { detail -> detail?.copy(events = detail.events.filterNot { it.origin == DataOrigin.SIMULATED }) }
        .asLoadState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    private val _action = MutableStateFlow(TripActionState())
    val action = _action.asStateFlow()

    private val _maneuverAlertId = MutableStateFlow<String?>(null)
    val maneuverAlertId = _maneuverAlertId.asStateFlow()

    fun finish() = perform(openHistory = true) { id -> repository.finishTrip(id) }

    private fun perform(openHistory: Boolean = false, block: suspend (Long) -> Unit) {
        if (_action.value.busy || _action.value.openHistory) return
        val active = (trip.value as? LoadState.Ready<TripWithEvents?>)?.value ?: return
        _action.value = TripActionState(busy = true)
        viewModelScope.launch {
            try {
                block(active.trip.id)
                _action.value = TripActionState(openHistory = openHistory)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _action.value = TripActionState(failed = true)
            }
        }
    }

    fun navigationHandled() { _action.value = TripActionState() }
}
