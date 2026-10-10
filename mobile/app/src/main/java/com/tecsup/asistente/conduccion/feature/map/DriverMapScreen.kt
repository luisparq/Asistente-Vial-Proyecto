package com.tecsup.asistente.conduccion.feature.map

import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.tecsup.asistente.conduccion.BuildConfig
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.*

@Composable
fun DriverMapScreen(onBack:()->Unit,viewModel:DriverMapViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var center by remember { mutableIntStateOf(0) }
    var mapError by remember { mutableStateOf<String?>(null) }
    ScreenLayout("Mi mapa","Mapa opcional · GPS del dispositivo BLE, no del teléfono",onBack) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(when(state.gpsStatus) {
                GpsStatus.VALID -> if(state.position!=null) "Posición GPS válida" else "Posición GPS no disponible"
                GpsStatus.STALE -> "Posición GPS caducada"
                GpsStatus.NO_FIX -> "GPS sin fix válido"
                GpsStatus.NO_RECEPTION -> "GPS sin recepción"
                GpsStatus.DISCONNECTED -> "Dispositivo BLE desconectado"
                GpsStatus.UNAVAILABLE -> "GPS no disponible en el dispositivo"
                GpsStatus.INVALID_FRAME -> "Datos GPS inválidos"
                GpsStatus.WAITING -> "Esperando posición GPS"
            },style=MaterialTheme.typography.titleMedium)
            state.position?.let { Text("Latitud: ${it.latitude} · Longitud: ${it.longitude}") }
            Button(onClick={center++},enabled=state.position!=null) { Text("Centrar mi posición") }
        } }
        Text("Turquesa: posición actual · Ámbar: eventos SIMULATED del recorrido activo")
        Text("${state.events.size} eventos de prueba con ubicación. No son detecciones físicas ni zonas de riesgo comprobadas.")
        mapError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        NativeDriverMap(state,center,{mapError=it})
        Text("La cartografía requiere Internet. El monitoreo, los recorridos y la sincronización funcionan sin mantener este mapa abierto.")
    }
}

@Composable
private fun NativeDriverMap(state:DriverMapState,center:Int,onError:(String)->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val currentState by rememberUpdatedState(state)
    val error by rememberUpdatedState(onError)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var firstCenter by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val view=remember(context) { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }
    DisposableEffect(view,lifecycle) {
        var alive=true
        var started=false;var resumed=false
        fun synchronize() {
            val wantStart=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            val wantResume=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if(resumed && !wantResume){view.onPause();resumed=false}
            if(started && !wantStart){view.onStop();started=false}
            if(!started && wantStart){view.onStart();started=true}
            if(!resumed && wantResume){view.onResume();resumed=true}
        }
        val observer=LifecycleEventObserver { _,_->synchronize() }
        lifecycle.addObserver(observer);synchronize()
        view.addOnDidFailLoadingMapListener { error("No se pudo cargar la cartografía. Revisa Internet o el proveedor configurado.") }
        view.getMapAsync { loaded ->
            if(!alive) return@getMapAsync
            map=loaded
            loaded.setStyle(BuildConfig.MAP_STYLE_URL) { style ->
                if(!alive) return@setStyle
                style.addSource(GeoJsonSource("current-gps","{\"type\":\"FeatureCollection\",\"features\":[]}"))
                style.addSource(GeoJsonSource("trip-events","{\"type\":\"FeatureCollection\",\"features\":[]}"))
                style.addLayer(CircleLayer("event-points","trip-events").withProperties(circleColor(Color.rgb(217,151,32)),circleRadius(7f),circleStrokeColor(Color.WHITE),circleStrokeWidth(2f)))
                style.addLayer(CircleLayer("gps-point","current-gps").withProperties(circleColor(Color.rgb(20,170,160)),circleRadius(9f),circleStrokeColor(Color.WHITE),circleStrokeWidth(3f)))
                ready=true
            }
            loaded.addOnMapClickListener { coordinate ->
                val hit=loaded.queryRenderedFeatures(loaded.projection.toScreenLocation(coordinate),"event-points").firstOrNull()
                val id=hit?.getNumberProperty("event_id")?.toLong()
                val event=currentState.events.firstOrNull { it.id==id }
                selected=event?.let { "Evento SIMULATED · ${it.type}\n"+com.tecsup.asistente.conduccion.core.ui.formatTimestamp(it.occurredAt)+"\n${it.latitude}, ${it.longitude}" }
                event!=null
            }
        }
        onDispose { alive=false;lifecycle.removeObserver(observer);if(resumed)view.onPause();if(started)view.onStop();ready=false;map=null;view.onDestroy() }
    }
    LaunchedEffect(state,ready) {
        if(!ready) return@LaunchedEffect
        fun feature(lat:Double,lon:Double,id:Long?=null)=JSONObject().put("type","Feature")
            .put("geometry",JSONObject().put("type","Point").put("coordinates",JSONArray().put(lon).put(lat)))
            .put("properties",JSONObject().apply { if(id!=null)put("event_id",id) })
        fun collection(array:JSONArray)=JSONObject().put("type","FeatureCollection").put("features",array).toString()
        val gps=JSONArray();state.position?.let { gps.put(feature(it.latitude,it.longitude)) }
        val events=JSONArray();state.events.forEach { events.put(feature(it.latitude!!,it.longitude!!,it.id)) }
        map?.style?.getSourceAs<GeoJsonSource>("current-gps")?.setGeoJson(collection(gps))
        map?.style?.getSourceAs<GeoJsonSource>("trip-events")?.setGeoJson(collection(events))
        if(!firstCenter && state.position!=null){map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(state.position.latitude,state.position.longitude),15.0));firstCenter=true}
        if(state.events.isEmpty())selected=null
    }
    LaunchedEffect(center,ready) { if(center>0 && ready)state.position?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),15.0)) } }
    LaunchedEffect(state.tripId) { selected=null }
    AndroidView(factory={view},modifier=Modifier.fillMaxWidth().height(420.dp))
    selected?.let { Text(it) }
}
