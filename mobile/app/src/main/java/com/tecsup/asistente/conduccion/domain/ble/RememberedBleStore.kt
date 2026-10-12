package com.tecsup.asistente.conduccion.domain.ble

import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Serializable
data class RememberedBleDevice(val address: String, val name: String?, val automatic: Boolean) {
    fun device() = BleDevice(address, name)
}
interface RememberedBleStore {
    fun read(owner: String): RememberedBleDevice?
    fun save(owner: String, device: RememberedBleDevice?)
}
data class BleReconnectState(val enabled: Boolean = false, val running: Boolean = false,
    val attempt: Int = 0, val message: String? = null)
internal val noBleReconnect: StateFlow<BleReconnectState> = MutableStateFlow(BleReconnectState())
