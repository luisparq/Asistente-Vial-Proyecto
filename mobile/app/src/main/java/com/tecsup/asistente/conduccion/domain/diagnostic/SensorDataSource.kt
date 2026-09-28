package com.tecsup.asistente.conduccion.domain.diagnostic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SensorDataStatus { PENDING_HARDWARE, WAITING_FOR_DATA, RECEIVING, FAILED }

/**
 * Punto de entrada de futuras notificaciones BLE. Los bytes no tienen formato
 * ni unidades definidos aún: el contrato y el decodificador pertenecen a 3B.
 */
interface SensorDataSource {
    val status: StateFlow<SensorDataStatus>
    val frames: Flow<ByteArray>
}
