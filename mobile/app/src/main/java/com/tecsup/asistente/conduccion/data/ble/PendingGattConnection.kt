package com.tecsup.asistente.conduccion.data.ble

import com.tecsup.asistente.conduccion.domain.ble.BleConnection
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState
import com.tecsup.asistente.conduccion.domain.ble.BleDevice
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No llama a connectGatt ni inventa un perfil de servicios o características. */
class PendingGattConnection @Inject constructor() : BleConnection {
    private val mutableState = MutableStateFlow(BleConnectionState())
    override val state = mutableState.asStateFlow()

    override fun select(device: BleDevice) {
        mutableState.value = BleConnectionState(ConnectionStatus.SELECTED, device)
    }

    override fun prepare() {
        val device = state.value.device ?: return
        mutableState.value = BleConnectionState(ConnectionStatus.AWAITING_GATT_PROFILE, device)
    }

    override fun clear() { mutableState.value = BleConnectionState() }
}
