package com.tecsup.asistente.conduccion.feature.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.ble.BleScanState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BleUiState(
    val scan: BleScanState = BleScanState(),
    val connection: BleConnectionState = BleConnectionState(),
)

@HiltViewModel
class BleViewModel @Inject constructor(private val repository: BleRepository) : ViewModel() {
    val state = combine(repository.scan, repository.connection, ::BleUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BleUiState())

    fun refresh() = repository.refresh()
    fun startScan() = repository.startScan()
    fun stopScan() = repository.stopScan()
    fun select(address: String) = repository.selectDevice(address)
    fun prepareConnection() = repository.prepareConnection()
    fun clearSelection() = repository.clearSelection()

    override fun onCleared() {
        repository.stopScan()
        super.onCleared()
    }
}
