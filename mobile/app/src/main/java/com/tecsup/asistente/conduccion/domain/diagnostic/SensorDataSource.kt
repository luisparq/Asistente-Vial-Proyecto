package com.tecsup.asistente.conduccion.domain.diagnostic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class SensorDataStatus { DISCONNECTED, WAITING_FOR_DATA, RECEIVING, FAILED }

/**
 * Telemetría IMU_RAW en memoria. No crea DrivingEvent ni escribe en Room/API.
 */
interface SensorDataSource {
    val relayDrops: StateFlow<Int> get() = EMPTY_RELAY_DROPS
    val relayFrames: Flow<SensorRelaySample> get() = kotlinx.coroutines.flow.emptyFlow()
    val relayIdentity: StateFlow<Boolean> get() = EMPTY_RELAY_IDENTITY
    val gps: StateFlow<GpsTelemetry> get() = EMPTY_GPS
    val status: StateFlow<SensorDataStatus>
    val frames: Flow<ByteArray>
    val sample: StateFlow<RawImuSample?>
}

private val EMPTY_GPS = kotlinx.coroutines.flow.MutableStateFlow(GpsTelemetry())

private val EMPTY_RELAY_IDENTITY=kotlinx.coroutines.flow.MutableStateFlow(false)

private val EMPTY_RELAY_DROPS=kotlinx.coroutines.flow.MutableStateFlow(0)
