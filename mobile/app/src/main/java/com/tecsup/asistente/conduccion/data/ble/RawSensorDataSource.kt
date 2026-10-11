package com.tecsup.asistente.conduccion.data.ble

import com.tecsup.asistente.conduccion.core.di.BleScope
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import javax.inject.Inject
import javax.inject.Singleton
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

@Singleton
class RawSensorDataSource @Inject constructor(@BleScope private val scope: CoroutineScope, private val clock: Clock = Clock.systemUTC()) : SensorDataSource {
    private val mutableStatus = MutableStateFlow(SensorDataStatus.DISCONNECTED)
    override val status = mutableStatus.asStateFlow()
    private val mutableSample = MutableStateFlow<RawImuSample?>(null)
    override val sample = mutableSample.asStateFlow()
    private val mutableFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames = mutableFrames.asSharedFlow()
    private var freshness: Job? = null
    private val mutableGps = MutableStateFlow(GpsTelemetry())
    override val gps = mutableGps.asStateFlow()
    private var gpsFreshness: Job? = null
    fun gpsUnavailable() {
        gpsFreshness?.cancel()
        mutableGps.value = GpsTelemetry(GpsStatus.UNAVAILABLE)
    }
    fun acceptGps(bytes: ByteArray) {
        gpsFreshness?.cancel()
        val value = try { GpsDecoder.decode(bytes) } catch (_: IllegalArgumentException) {
            mutableGps.value = GpsTelemetry(GpsStatus.INVALID_FRAME); return
        }
        mutableGps.value = value.copy(receivedAtMillis = clock.millis(), receivedAtMonotonicMillis = System.nanoTime() / 1_000_000)
        gpsFreshness = scope.launch {
            if (value.status == GpsStatus.VALID) {
                delay(GpsDecoder.MAX_AGE_MILLIS - (value.ageMillis ?: 0) + 1)
                mutableGps.value = GpsTelemetry(GpsStatus.STALE)
            } else {
                delay(GpsDecoder.MAX_AGE_MILLIS + 1)
                mutableGps.value = GpsTelemetry(GpsStatus.WAITING)
            }
        }
    }

    fun reset(status: SensorDataStatus) {
        gpsFreshness?.cancel()
        mutableGps.value = GpsTelemetry(if (status == SensorDataStatus.WAITING_FOR_DATA) GpsStatus.WAITING else GpsStatus.DISCONNECTED)
        freshness?.cancel()
        freshness = null
        mutableSample.value = null
        mutableStatus.value = status
    }

    // Invocado exclusivamente desde el scope BLE serializado del conector.
    fun accept(bytes: ByteArray) {
        if (bytes.size != ImuRawDecoder.FRAME_SIZE) {
            freshness?.cancel()
            mutableSample.value = null
            mutableStatus.value = SensorDataStatus.FAILED
            return
        }
        val copy = bytes.copyOf()
        mutableSample.value = ImuRawDecoder.decode(copy, clock.millis())
        mutableStatus.value = SensorDataStatus.RECEIVING
        mutableFrames.tryEmit(copy)
        freshness?.cancel()
        freshness = scope.launch {
            delay(STALE_AFTER_MILLIS)
            mutableSample.value = null
            mutableStatus.value = SensorDataStatus.WAITING_FOR_DATA
        }
    }

    companion object { const val STALE_AFTER_MILLIS = 2_000L }
}
