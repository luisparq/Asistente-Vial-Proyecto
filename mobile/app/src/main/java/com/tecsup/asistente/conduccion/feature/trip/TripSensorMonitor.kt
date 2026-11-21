package com.tecsup.asistente.conduccion.feature.trip

import android.util.Log
import com.tecsup.asistente.conduccion.data.local.VehicleProfileStore
import com.tecsup.asistente.conduccion.data.repository.RoomTripRepository
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus
import com.tecsup.asistente.conduccion.domain.diagnostic.ImuRawDecoder
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.processing.ImuConverter
import com.tecsup.asistente.conduccion.domain.processing.NormalManeuver
import com.tecsup.asistente.conduccion.domain.processing.VehicleLearning
import com.tecsup.asistente.conduccion.domain.processing.VehicleExcess
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

data class TripSensorNotice(
    val tripId: Long,
    val type: DrivingEventType,
    val excess: VehicleExcess,
    val buzzerSent: Boolean,
)

@Singleton
class TripSensorMonitor @Inject constructor(
    private val profiles: VehicleProfileStore,
    private val ble: BleRepository,
    private val source: SensorDataSource,
    private val trips: RoomTripRepository,
) {
    private val latestNotice = MutableStateFlow<TripSensorNotice?>(null)
    val notice = latestNotice.asStateFlow()

    suspend fun observe(onEvent: (TripSensorNotice) -> Unit) {
        combine(trips.observeActiveTrip(), ble.connection) { trip, connection ->
            val address = connection.device?.address
            if (trip != null && address != null && connection.status == ConnectionStatus.CONNECTED) {
                trip.id to address
            } else null
        }.distinctUntilChanged().collectLatest { route ->
            if (route == null) return@collectLatest
            val (tripId, address) = route
            val profile = withContext(Dispatchers.IO) {
                try {
                    profiles.load(address)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("TripSensorMonitor", "No se pudo leer el perfil de calibración", error)
                    null
                }
            } ?: return@collectLatest
            val detector = VehicleLearning().apply { restore(profile) }
            val converter = ImuConverter()
            source.frames.collect { bytes ->
                if (bytes.size != ImuRawDecoder.FRAME_SIZE) return@collect
                val raw = ImuRawDecoder.decode(bytes, System.nanoTime() / 1_000_000)
                val detection = detector.detect(converter.convert(raw)) ?: return@collect
                val type = when (detection.kind) {
                    NormalManeuver.ACCELERATION -> DrivingEventType.HARSH_ACCELERATION
                    NormalManeuver.BRAKING -> DrivingEventType.HARSH_BRAKING
                    NormalManeuver.TURN -> DrivingEventType.SHARP_TURN
                }
                try {
                    if (trips.recordSensorManeuver(tripId, type)) {
                        val buzzerSent = ble.triggerBuzzer()
                        val notice = TripSensorNotice(tripId, type, detection, buzzerSent)
                        latestNotice.value = notice
                        onEvent(notice)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("TripSensorMonitor", "No se pudo guardar el evento del sensor", error)
                }
            }
        }
    }
}
