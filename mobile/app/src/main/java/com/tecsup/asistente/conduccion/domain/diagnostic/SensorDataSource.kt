package com.tecsup.asistente.conduccion.domain.diagnostic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SensorDataStatus { DISCONNECTED, WAITING_FOR_DATA, RECEIVING, FAILED }

/**
 * Telemetría IMU_RAW en memoria. No crea DrivingEvent ni escribe en Room/API.
 */
interface SensorDataSource {
    val gps: StateFlow<GpsTelemetry> get() = EMPTY_GPS
    val status: StateFlow<SensorDataStatus>
    val frames: Flow<ByteArray>
    val sample: StateFlow<RawImuSample?>
}

private val EMPTY_GPS = kotlinx.coroutines.flow.MutableStateFlow(GpsTelemetry())
