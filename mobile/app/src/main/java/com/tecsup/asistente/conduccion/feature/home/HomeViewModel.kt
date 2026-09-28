package com.tecsup.asistente.conduccion.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.asLoadState
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StartTripState(
    val busy: Boolean = false,
    val failed: Boolean = false,
    val openTrip: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: TripRepository) : ViewModel() {
    val activeTrip = repository.observeActiveTrip().asLoadState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)
    private val _action = MutableStateFlow(StartTripState())
    val action = _action.asStateFlow()

    fun startOrResume() {
        if (_action.value.busy || _action.value.openTrip) return
        _action.value = StartTripState(busy = true)
        viewModelScope.launch {
            try {
                repository.startOrResumeTrip()
                _action.value = StartTripState(openTrip = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _action.value = StartTripState(failed = true)
            }
        }
    }

    fun navigationHandled() { _action.value = StartTripState() }
}
