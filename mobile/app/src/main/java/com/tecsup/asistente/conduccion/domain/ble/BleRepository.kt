package com.tecsup.asistente.conduccion.domain.ble

import kotlinx.coroutines.flow.StateFlow

interface BleRepository {
    val reconnection: StateFlow<BleReconnectState> get() = noBleReconnect
    fun setForeground(value: Boolean) {}
    fun enableReconnection() {}
    fun disconnectManual() = clearSelection()
    val scan: StateFlow<BleScanState>
    val connection: StateFlow<BleConnectionState>
    fun refresh()
    fun startScan()
    fun stopScan()
    fun selectDevice(address: String)
    fun prepareConnection()
    fun clearSelection()
}
