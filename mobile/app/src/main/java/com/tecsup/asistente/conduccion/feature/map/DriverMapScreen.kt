package com.tecsup.asistente.conduccion.feature.map

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.tecsup.asistente.conduccion.BuildConfig
import com.tecsup.asistente.conduccion.core.ui.AvigemColors
import com.tecsup.asistente.conduccion.core.ui.LocalMainNav
import com.tecsup.asistente.conduccion.core.ui.MainNavBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverMapScreen(onBack:()->Unit, onDevice:()->Unit = {}, viewModel:DriverMapViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val layers by viewModel.layers.collectAsStateWithLifecycle()
    val selectedLayers by viewModel.selectedLayers.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val tileStatus by viewModel.tiles.status.collectAsStateWithLifecycle()
    val now by rememberRoadTime()
    val catalog = layers[RoadLayer.SPEED]?.catalog ?: RoadCatalog(osmAvailable = false)
    val trafficTemplate = if (RoadLayer.FLOW in selectedLayers) viewModel.tiles.template(session, now / 60) else null
    val trafficMessage = when {
        RoadLayer.FLOW !in selectedLayers -> "Tráfico desactivado"
        session?.token.isNullOrBlank() -> "Inicia sesión para consultar TomTom"
        tileStatus.ownerKey == session?.ownerKey -> tileStatus.message
        else -> "Cargando tráfico…"
    }
    var showLayers by remember { mutableStateOf(false) }
    var center by remember { mutableIntStateOf(0) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val connected = state.gpsStatus != GpsStatus.DISCONNECTED
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { LocalMainNav.current?.let { MainNavBar(it) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Mapa a pantalla completa, centrado en Arequipa hasta tener posición GPS.
            key(session) {
                NativeDriverMap(state, center, Modifier.fillMaxSize(),
                    roadLayers = layers, selectedLayers = selectedLayers, trafficTemplate = trafficTemplate,
                    tileClient = viewModel.tiles, onAreaChanged = viewModel::viewArea,
                    onError = { mapError = it })
            }
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onDevice,
                        modifier = Modifier.weight(1f, fill = false),
                        shape = MaterialTheme.shapes.small,
                        color = if (connected) androidx.compose.ui.graphics.Color(0xFF1E7F43) else androidx.compose.ui.graphics.Color(0xFFB3261E),
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color.White))
                            Text(if (connected) "AVIGEM One conectado" else "AVIGEM One desconectado", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    FilledTonalButton(onClick = { showLayers = true }, shape = MaterialTheme.shapes.small) { Text("Capas") }
                }
                mapError?.let {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                        Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showInfo) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Column(Modifier.widthIn(max = 360.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(when(state.gpsStatus) {
                                GpsStatus.VALID -> if(state.position!=null) "Posición GPS válida" else "Posición GPS no disponible"
                                GpsStatus.STALE -> "Posición GPS caducada"
                                GpsStatus.NO_FIX -> "GPS sin fix válido"
                                GpsStatus.NO_RECEPTION -> "GPS sin recepción"
                                GpsStatus.DISCONNECTED -> "Dispositivo BLE desconectado"
                                GpsStatus.UNAVAILABLE -> "GPS no disponible en el dispositivo"
                                GpsStatus.INVALID_FRAME -> "Datos GPS inválidos"
                                GpsStatus.WAITING -> "Esperando posición GPS"
                            }, style = MaterialTheme.typography.titleSmall)
                            state.position?.let { Text("Latitud: ${it.latitude} · Longitud: ${it.longitude}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text("Citrino: tu posición · Ámbar: maniobras de prueba (${state.events.size}). Toca puntos y tramos para ver sus datos. © OpenStreetMap y TomTom.", style = MaterialTheme.typography.labelMedium)
                            RoadAlertPanel(catalog, layers[RoadLayer.SPEED]?.catalog != null,
                                state.position?.let { RoadPoint(it.latitude, it.longitude) },
                                catalogMessage = layers[RoadLayer.SPEED]?.message ?: "Cargando límites de velocidad…",
                                trafficMessage = trafficMessage, speak = state.tripId == null)
                            Text("La cartografía requiere Internet. El monitoreo, los recorridos y la sincronización funcionan sin mantener este mapa abierto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { showInfo = !showInfo }, shape = MaterialTheme.shapes.small) { Text(if (showInfo) "Ocultar detalles" else "Detalles y alertas") }
                    Button(onClick = { center++ }, enabled = state.position != null, shape = MaterialTheme.shapes.small) { Text("Mi posición") }
                }
            }
        }
    }
    if (showLayers) {
        ModalBottomSheet(onDismissRequest = { showLayers = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Capas del mapa", style = MaterialTheme.typography.titleLarge)
                Text("Colegios y auxilio se buscan alrededor del centro del mapa.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RoadLayer.entries.forEach { layer ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(layer.label, style = MaterialTheme.typography.titleSmall)
                            Text(if (layer == RoadLayer.FLOW) trafficMessage else layers[layer]?.message ?: "Desactivado",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = layer in selectedLayers, onCheckedChange = { viewModel.toggleLayer(layer) },
                            modifier = Modifier.semantics { contentDescription = layer.label })
                    }
                }
            }
        }
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
    center: Int,
    modifier: Modifier,
    preview: Boolean = false,
    roadLayers: Map<RoadLayer, RoadLayerData> = emptyMap(),
    selectedLayers: Set<RoadLayer> = emptySet(),
    trafficTemplate: String? = null,
    tileClient: RoadTileClient? = null,
    onAreaChanged: (RoadPoint) -> Unit = {},
    onReady: () -> Unit = {},
    onError: (String) -> Unit,
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val currentState by rememberUpdatedState(state)
    val error by rememberUpdatedState(onError)
    val showMap by rememberUpdatedState(onReady)
    val areaChanged by rememberUpdatedState(onAreaChanged)
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var ready by remember { mutableStateOf(false) }
    var firstCenter by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val view = remember(context, preview) {
        tileClient?.install()
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
            } else {
                loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(ArequipaReference, 13.0))
            }
            loaded.setStyle(BuildConfig.MAP_STYLE_URL) { style ->
                if(!alive) return@setStyle
                style.addSource(GeoJsonSource("current-gps","{\"type\":\"FeatureCollection\",\"features\":[]}"))
                style.addSource(GeoJsonSource("trip-events","{\"type\":\"FeatureCollection\",\"features\":[]}"))
                addRoadMapLayers(style)
                style.addSource(GeoJsonSource("road-limits", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
                style.addLayer(LineLayer("road-limit-lines", "road-limits").withProperties(
                    lineColor(Color.rgb(42, 117, 224)), lineWidth(5f)
                ))
                style.addLayer(CircleLayer("event-points","trip-events").withProperties(circleColor(Color.rgb(201,138,18)),circleRadius(7f),circleStrokeColor(Color.WHITE),circleStrokeWidth(2f)))
                style.addLayer(CircleLayer("gps-point","current-gps").withProperties(circleColor(Color.rgb(212,242,58)),circleRadius(9f),circleStrokeColor(Color.rgb(21,22,26)),circleStrokeWidth(3f)))
                ready=true
            }
            loaded.addOnCameraIdleListener {
                if (!preview) loaded.cameraPosition.target?.let { areaChanged(RoadPoint(it.latitude, it.longitude)) }
            }
            loaded.addOnMapClickListener { coordinate ->
                val hit=loaded.queryRenderedFeatures(loaded.projection.toScreenLocation(coordinate),"event-points").firstOrNull()
                val roadIds = RoadLayer.entries.filter { it != RoadLayer.FLOW }.flatMap { roadLayerIds(it).toList() }
                val roadHit = loaded.queryRenderedFeatures(loaded.projection.toScreenLocation(coordinate), *roadIds.toTypedArray()).firstOrNull()
                val id=hit?.getNumberProperty("event_id")?.toLong()
                val event=currentState.events.firstOrNull { it.id==id }
                selected=event?.let { "Maniobra de prueba · ${it.type}\n"+com.tecsup.asistente.conduccion.core.ui.formatTimestamp(it.occurredAt)+"\n${it.latitude}, ${it.longitude}" }
                if (event == null) selected = roadHit?.getStringProperty("road_label")
                event != null || roadHit != null
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
        if (!preview && !firstCenter && state.position != null) {
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(state.position.latitude, state.position.longitude), 15.0))
            firstCenter = true
        }
    }
    LaunchedEffect(roadLayers, selectedLayers, ready) {
        if (ready) map?.style?.let { updateRoadMapLayers(it, roadLayers, selectedLayers) }
    }
    LaunchedEffect(trafficTemplate, ready) {
        if (ready) map?.style?.let { updateTrafficLayer(it, trafficTemplate) }
    }
    LaunchedEffect(state.position, ready, preview) {
        if (!preview || !ready) return@LaunchedEffect
        val position = state.position
        val target = position?.let { LatLng(it.latitude, it.longitude) } ?: ArequipaReference
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(target, if (position == null) 12.0 else 15.0))
    }
    LaunchedEffect(center,ready) { if(center>0 && ready)state.position?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),15.0)) } }
    LaunchedEffect(state.tripId) { selected=null }
    Box(modifier) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
        selected?.let {
            Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), shape = MaterialTheme.shapes.small,
                color = AvigemColors.Volcanic, contentColor = AvigemColors.Sillar100) {
                Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
