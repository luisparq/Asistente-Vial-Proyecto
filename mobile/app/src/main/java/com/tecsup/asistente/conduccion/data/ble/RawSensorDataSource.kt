package com.tecsup.asistente.conduccion.data.ble

import com.tecsup.asistente.conduccion.core.di.BleScope
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

@Singleton
class RawSensorDataSource @Inject constructor(@BleScope private val scope: CoroutineScope) : SensorDataSource {
    private val mutableStatus = MutableStateFlow(SensorDataStatus.DISCONNECTED)
    override val status = mutableStatus.asStateFlow()
    private val mutableSample = MutableStateFlow<RawImuSample?>(null)
    override val sample = mutableSample.asStateFlow()
    private val mutableFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val frames = mutableFrames.asSharedFlow()
    private var freshness: Job? = null

    fun reset(status: SensorDataStatus) {
        freshness?.cancel()
        freshness = null
        mutableSample.value = null
        mutableStatus.value = status
    }

    // Invocado exclusivamente desde el scope BLE serializado del conector.
    fun accept(bytes: ByteArray) {
        if (bytes.size != ImuRawDecoder.FRAME_SIZE) {
            reset(SensorDataStatus.FAILED)
            return
        }
        val copy = bytes.copyOf()
        mutableSample.value = ImuRawDecoder.decode(copy)
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
