package com.tecsup.asistente.conduccion.domain.ble

import kotlinx.coroutines.flow.StateFlow

/** Conexión del dispositivo seleccionado; prepare inicia descubrimiento y suscripción GATT. */
interface BleConnection {
    val state: StateFlow<BleConnectionState>
    fun select(device: BleDevice)
    fun prepare()
    fun clear()
    fun triggerBuzzer(): Boolean = false
    val buzzerDone: kotlinx.coroutines.flow.SharedFlow<Unit> get() = kotlinx.coroutines.flow.MutableSharedFlow()
}
