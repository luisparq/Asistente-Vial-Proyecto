package com.tecsup.asistente.conduccion.domain.ble

import kotlinx.coroutines.flow.StateFlow

/** Se sustituirá por el transporte GATT cuando exista el perfil real del ESP32. */
interface BleConnection {
    val state: StateFlow<BleConnectionState>
    fun select(device: BleDevice)
    fun prepare()
    fun clear()
}
