package com.tecsup.asistente.conduccion.feature.map

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.tecsup.asistente.conduccion.BuildConfig
import com.tecsup.asistente.conduccion.core.ui.AvigemColors
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.domain.diagnostic.GpsStatus
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.*
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.*
import kotlinx.coroutines.delay

private val ArequipaReference = LatLng(-16.3989, -71.5369)

@Composable
fun DriverMapScreen(onBack:()->Unit,viewModel:DriverMapViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val registeredCatalog by rememberRoadCatalog(viewModel.catalogUrl)
    val now by rememberRoadTime()
    val catalog = registeredCatalog?.activeAt(now) ?: RoadCatalog(osmAvailable = false)
    var center by remember { mutableIntStateOf(0) }
    var mapError by remember { mutableStateOf<String?>(null) }
    ScreenLayout("Mi mapa", "GPS del dispositivo BLE", onBack) {
        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
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
            state.position?.let { Text("Latitud: ${it.latitude} · Longitud: ${it.longitude}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick={center++},enabled=state.position!=null,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=MaterialTheme.shapes.small) { Text("Centrar posición") }
        } }
        Text("Punto grande citrino: posición actual · Punto ámbar: datos de prueba SIMULATED del recorrido activo", style=MaterialTheme.typography.labelMedium)
        Text("${state.events.size} eventos de prueba con ubicación. No son detecciones físicas ni zonas de riesgo comprobadas.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        mapError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        RoadAlertPanel(catalog, registeredCatalog != null, state.position?.let { RoadPoint(it.latitude, it.longitude) })
        Text("Azul: límite · Violeta: colegio · Rojo: tráfico · Datos © OpenStreetMap y TomTom",
            style = MaterialTheme.typography.labelMedium)
        NativeDriverMap(state, catalog, center, Modifier.fillMaxWidth().height(420.dp).clip(MaterialTheme.shapes.large),
            onError = { mapError = it })
        Text("La cartografía requiere Internet. El monitoreo, los recorridos y la sincronización funcionan sin mantener este mapa abierto.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DriverMapPreview(
    modifier: Modifier = Modifier,
    viewModel: DriverMapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(ready, failed) {
        if (!ready && !failed) {
            delay(20_000)
            failed = true
        }
    }
    Box(modifier.background(colors.surfaceVariant)) {
        NativeDriverMap(
            state = state.copy(events = emptyList()),
            catalog = RoadCatalog(osmAvailable = false),
            center = 0,
            modifier = Modifier.matchParentSize(),
            preview = true,
            onReady = { ready = true; failed = false },
            onError = { failed = true },
        )
        if (!ready || failed) {
            Column(
                Modifier.matchParentSize().background(colors.surfaceVariant).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (!failed) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(
                    if (failed) "Cartografía no disponible" else "Cargando mapa…",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            shape = MaterialTheme.shapes.small,
            color = AvigemColors.Volcanic,
            contentColor = AvigemColors.Sillar100,
        ) {
            Text(
                if (state.position != null) "GPS de AVIGEM One" else "Arequipa · Vista de referencia",
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun NativeDriverMap(
    state: DriverMapState,
    catalog: RoadCatalog,
    center: Int,
    modifier: Modifier,
    preview: Boolean = false,
    onReady: () -> Unit = {},
    onError: (String) -> Unit,
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val currentState by rememberUpdatedState(state)
    val error by rememberUpdatedState(onError)
    val showMap by rememberUpdatedState(onReady)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var firstCenter by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val view = remember(context, preview) {
        MapLibre.getInstance(context)
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(preview)
        MapView(context, options).apply { onCreate(null) }
    }
    DisposableEffect(view,lifecycle) {
        var alive=true
        var previewRendered = false
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
        view.addOnDidFailLoadingMapListener {
            if (alive) error("No se pudo cargar la cartografía. Revisa Internet o el proveedor configurado.")
        }
        if (preview) {
            view.addOnDidFinishRenderingMapListener { fullyRendered ->
                if (alive && ready && fullyRendered && !previewRendered) {
                    previewRendered = true
                    showMap()
                }
            }
        }
        view.getMapAsync { loaded ->
            if(!alive) return@getMapAsync
            map=loaded
            if (preview) {
                loaded.uiSettings.setAllGesturesEnabled(false)
                loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(ArequipaReference, 12.0))
            }
            loaded.setStyle(BuildConfig.MAP_STYLE_URL) { style ->
                if(!alive) return@setStyle
                style.addSource(GeoJsonSource("current-gps","{\"type\":\"FeatureCollection\",\"features\":[]}"))
                style.addSource(GeoJsonSource("trip-events","{\"type\":\"FeatureCollection\",\"features\":[]}"))
                for ((id, color) in listOf("road-zones" to Color.rgb(138, 79, 202), "road-traffic" to Color.rgb(205, 55, 58))) {
                    style.addSource(GeoJsonSource(id, "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                    style.addLayer(CircleLayer("$id-points", id).withProperties(
                        circleColor(color), circleRadius(8f), circleStrokeColor(Color.WHITE), circleStrokeWidth(2f)
                    ))
                }
                style.addSource(GeoJsonSource("road-limits", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                style.addLayer(LineLayer("road-limit-lines", "road-limits").withProperties(
                    lineColor(Color.rgb(42, 117, 224)), lineWidth(5f)
                ))
                style.addLayer(CircleLayer("event-points","trip-events").withProperties(circleColor(Color.rgb(201,138,18)),circleRadius(7f),circleStrokeColor(Color.WHITE),circleStrokeWidth(2f)))
                style.addLayer(CircleLayer("gps-point","current-gps").withProperties(circleColor(Color.rgb(212,242,58)),circleRadius(9f),circleStrokeColor(Color.rgb(21,22,26)),circleStrokeWidth(3f)))
                ready=true
            }
            loaded.addOnMapClickListener { coordinate ->
                val hit=loaded.queryRenderedFeatures(loaded.projection.toScreenLocation(coordinate),"event-points").firstOrNull()
                val roadHit = loaded.queryRenderedFeatures(loaded.projection.toScreenLocation(coordinate),
                    "road-zones-points", "road-traffic-points", "road-limit-lines").firstOrNull()
                val id=hit?.getNumberProperty("event_id")?.toLong()
                val event=currentState.events.firstOrNull { it.id==id }
                selected=event?.let { "Evento SIMULATED · ${it.type}\n"+com.tecsup.asistente.conduccion.core.ui.formatTimestamp(it.occurredAt)+"\n${it.latitude}, ${it.longitude}" }
                if (event == null) selected = roadHit?.getStringProperty("road_label")
                event != null || roadHit != null
            }
        }
        onDispose { alive=false;lifecycle.removeObserver(observer);if(resumed)view.onPause();if(started)view.onStop();ready=false;map=null;view.onDestroy() }
    }
    LaunchedEffect(state,catalog,ready) {
        if(!ready) return@LaunchedEffect
        fun feature(lat:Double,lon:Double,id:Long?=null)=JSONObject().put("type","Feature")
            .put("geometry",JSONObject().put("type","Point").put("coordinates",JSONArray().put(lon).put(lat)))
            .put("properties",JSONObject().apply { if(id!=null)put("event_id",id) })
        fun collection(array:JSONArray)=JSONObject().put("type","FeatureCollection").put("features",array).toString()
        val gps=JSONArray();state.position?.let { gps.put(feature(it.latitude,it.longitude)) }
        val events=JSONArray();state.events.forEach { events.put(feature(it.latitude!!,it.longitude!!,it.id)) }
        map?.style?.getSourceAs<GeoJsonSource>("current-gps")?.setGeoJson(collection(gps))
        map?.style?.getSourceAs<GeoJsonSource>("trip-events")?.setGeoJson(collection(events))
        val zones = JSONArray()
        catalog.zones.forEach {
            zones.put(feature(it.point.lat, it.point.lon).put("properties", JSONObject().put("road_label", it.name)))
        }
        val traffic = JSONArray()
        catalog.traffic.forEach {
            traffic.put(feature(it.point.lat, it.point.lon).put("properties", JSONObject().put("road_label", "Tráfico reportado")))
        }
        val limits = JSONArray()
        catalog.speedSections.forEach {
            val coordinates = JSONArray().put(JSONArray().put(it.start.lon).put(it.start.lat))
                .put(JSONArray().put(it.end.lon).put(it.end.lat))
            limits.put(JSONObject().put("type", "Feature")
                .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordinates))
                .put("properties", JSONObject().put("road_label", "Límite: ${it.kmh} km/h")))
        }
        map?.style?.getSourceAs<GeoJsonSource>("road-zones")?.setGeoJson(collection(zones))
        map?.style?.getSourceAs<GeoJsonSource>("road-traffic")?.setGeoJson(collection(traffic))
        map?.style?.getSourceAs<GeoJsonSource>("road-limits")?.setGeoJson(collection(limits))
        if (!preview && !firstCenter && state.position != null) {
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(state.position.latitude, state.position.longitude), 15.0))
            firstCenter = true
        }
    }
    LaunchedEffect(state.position, ready, preview) {
        if (!preview || !ready) return@LaunchedEffect
        val position = state.position
        val target = position?.let { LatLng(it.latitude, it.longitude) } ?: ArequipaReference
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target, if (position == null) 12.0 else 15.0))
    }
    LaunchedEffect(center,ready) { if(center>0 && ready)state.position?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),15.0)) } }
    LaunchedEffect(state.tripId) { selected=null }
    AndroidView(factory = { view }, modifier = modifier)
    selected?.let { Text(it) }
}
