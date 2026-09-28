package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiagnosticUiState(
    val connection: BleConnectionState = BleConnectionState(),
    val sensorStatus: SensorDataStatus = SensorDataStatus.PENDING_HARDWARE,
)

@HiltViewModel
class DiagnosticViewModel @Inject constructor(repository: BleRepository, source: SensorDataSource) : ViewModel() {
    val state = combine(repository.connection, source.status, ::DiagnosticUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticUiState())
}
