package com.tecsup.asistente.conduccion.domain.ble

import kotlinx.coroutines.flow.Flow

/** Frontera con Android; las pruebas pueden sustituirla sin usar una radio BLE. */
interface BlePlatform {
    fun availability(): BleAvailability
    fun observeAvailability(): Flow<BleAvailability>
    fun startScan(onDevice: (BleDevice) -> Unit, onFailure: (ScanFailure) -> Unit): ScanSession
}

fun interface ScanSession { fun stop() }
