package com.tecsup.asistente.conduccion.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.domain.model.*
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class MapPosition(val latitude: Double,val longitude: Double)
data class DriverMapState(val gpsStatus: GpsStatus = GpsStatus.DISCONNECTED,
    val position: MapPosition? = null,val events: List<DrivingEvent> = emptyList(),val tripId: Long? = null)
fun driverMapState(connection: BleConnectionState,gps: GpsTelemetry,trip: TripWithEvents?): DriverMapState {
    val connected=connection.status==ConnectionStatus.CONNECTED
    val valid=connected && gps.status==GpsStatus.VALID && gps.latitude != null && gps.longitude != null &&
        gps.latitude in -90.0..90.0 && gps.longitude in -180.0..180.0 && gps.ageMillis != null && gps.ageMillis in 0..GpsDecoder.MAX_AGE_MILLIS
    val active=trip?.takeIf { it.trip.status==TripStatus.ACTIVE }
    return DriverMapState(if(connected) gps.status else GpsStatus.DISCONNECTED,
        if(valid) MapPosition(gps.latitude!!,gps.longitude!!) else null,
        active?.events.orEmpty().filter { it.tripId==active?.trip?.id && it.origin==DataOrigin.SIMULATED && it.latitude != null && it.longitude != null &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 },active?.trip?.id)
}
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DriverMapViewModel @Inject constructor(ble: BleRepository,source: SensorDataSource,trips: TripRepository,
    sessions: SessionStore, repository: RoadLayerRepository, val tiles: RoadTileClient): ViewModel() {
    val session = sessions.state
    val selectedLayers = MutableStateFlow(setOf(RoadLayer.SPEED))
    private val area = MutableStateFlow(RoadPoint(-16.3989, -71.5369))
    val layers = combine(RoadLayer.entries.filter { it != RoadLayer.FLOW }.map { layer ->
        repository.observe(layer, selectedLayers, area).map { layer to it }
    }) { values -> values.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, replayExpirationMillis = 0), emptyMap())

    fun toggleLayer(layer: RoadLayer) {
        selectedLayers.update { selected -> if (layer in selected) selected - layer else selected + layer }
    }

    fun viewArea(point: RoadPoint) {
        if (point.valid() && area.value.distanceTo(point) >= 500) area.value = point
    }
    private val active = trips.observeActiveTrip().flatMapLatest { trip -> if(trip==null) flowOf(null) else trips.observeTrip(trip.id) }
    val state=combine(ble.connection,source.gps,active,::driverMapState)
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000,replayExpirationMillis=0),DriverMapState())
}
