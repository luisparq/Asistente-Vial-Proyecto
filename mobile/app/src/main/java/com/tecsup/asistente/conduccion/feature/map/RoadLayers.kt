package com.tecsup.asistente.conduccion.feature.map

import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import com.tecsup.asistente.conduccion.feature.login.ServerConfiguration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*

enum class RoadLayer(val label: String, val sourceId: String, val poiType: String? = null) {
    FLOW("Tráfico en vivo", "tomtom-flow"),
    INCIDENTS("Incidentes y cierres", "tomtom-incidents"),
    SCHOOLS("Colegios", "tomtom-schools"),
    HOSPITAL("Hospitales", "tomtom-hospital", "hospital"),
    POLICE("Comisarías", "tomtom-police", "police"),
    FUEL("Grifos", "tomtom-fuel", "fuel"),
    REPAIR("Talleres", "tomtom-repair", "repair"),
    SPEED("Límites de velocidad · OSM", "road-limits"),
}

const val EMPTY_ROAD_FEATURES = "{\"type\":\"FeatureCollection\",\"features\":[]}"

data class RoadLayerData(
    val enabled: Boolean = false,
    val features: String = EMPTY_ROAD_FEATURES,
    val message: String = "Desactivado",
    val catalog: RoadCatalog? = null,
)

fun roadServiceMessage(status: Int): String = when (status) {
    401 -> "Inicia sesión nuevamente para consultar TomTom"
    403 -> "Tu cuenta no tiene acceso a este servicio"
    429 -> "Límite de consultas alcanzado; actualización en pausa"
    503 -> "TomTom no está configurado o está temporalmente indisponible"
    else -> "El proveedor no pudo responder. Reintenta en unos momentos"
}

private val roadJson = Json { ignoreUnknownKeys = true }

/** Validates provider coordinates before constructing the GeoJSON rendered by MapLibre. */
internal fun roadFeatures(layer: RoadLayer, body: String): Pair<String, Int> {
    val root = roadJson.parseToJsonElement(body).jsonObject
    val key = if (layer == RoadLayer.INCIDENTS) "incidents" else if (layer == RoadLayer.SCHOOLS) "schools" else "pois"
    val features = buildJsonArray {
        root[key]?.jsonArray.orEmpty().forEach { element ->
            val item = element.jsonObject
            val geometry = if (layer == RoadLayer.INCIDENTS) {
                val raw = item["geometry"]?.jsonObject ?: return@forEach
                val type = raw["type"]?.jsonPrimitive?.content
                val coordinates = raw["coordinates"] as? JsonArray ?: return@forEach
                fun validPoint(value: JsonElement): Boolean {
                    val point = value as? JsonArray ?: return false
                    val lon = point.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return false
                    val lat = point.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return false
                    return lon.isFinite() && lat.isFinite() && lon in -180.0..180.0 && lat in -90.0..90.0
                }
                val valid = when (type) {
                    "Point" -> validPoint(coordinates)
                    "LineString" -> coordinates.size >= 2 && coordinates.all(::validPoint)
                    else -> false
                }
                if (!valid) return@forEach
                raw
            } else {
                val lat = item["lat"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
                val lon = item["lon"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
                if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@forEach
                buildJsonObject {
                    put("type", "Point")
                    putJsonArray("coordinates") { add(lon); add(lat) }
                }
            }
            val title = item["name"]?.jsonPrimitive?.contentOrNull
                ?: item["category"]?.jsonPrimitive?.contentOrNull ?: layer.label
            val description = item["description"]?.jsonPrimitive?.contentOrNull
                ?: item["address"]?.jsonPrimitive?.contentOrNull
            add(buildJsonObject {
                put("type", "Feature")
                put("geometry", geometry)
                putJsonObject("properties") {
                    put("road_label", listOfNotNull(title, description, "Fuente TomTom").joinToString("\n"))
                }
            })
        }
    }
    return buildJsonObject { put("type", "FeatureCollection"); put("features", features) }.toString() to features.size
}

@Singleton
class RoadLayerRepository @Inject constructor(
    private val api: LaravelApi,
    private val sessions: SessionStore,
    private val configuration: ServerConfiguration,
) {
    private val cooldowns = java.util.concurrent.ConcurrentHashMap<Pair<String, RoadLayer>, Long>()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun observe(layer: RoadLayer, selected: StateFlow<Set<RoadLayer>>, center: Flow<RoadPoint>): Flow<RoadLayerData> {
        val area = if (layer == RoadLayer.SPEED || layer == RoadLayer.INCIDENTS) {
            flowOf(RoadPoint(-16.3989, -71.5369))
        } else center.distinctUntilChanged().debounce(750)
        return combine(selected.map { layer in it }.distinctUntilChanged(), sessions.state, area) { enabled, session, point ->
            Triple(enabled, session, point)
        }.flatMapLatest { (enabled, session, point) ->
            flow {
                if (!enabled) { emit(RoadLayerData()); return@flow }
                if (layer != RoadLayer.SPEED && session?.token.isNullOrBlank()) {
                    emit(RoadLayerData(true, message = "Inicia sesión para consultar TomTom"))
                    return@flow
                }
                emit(RoadLayerData(true, message = "Cargando…"))
                val base = session?.baseUrl ?: configuration.baseUrl
                val key = (session?.ownerKey ?: base) to layer
                while (true) {
                    val wait = (cooldowns[key] ?: 0L) - android.os.SystemClock.elapsedRealtime()
                    if (wait > 0L) {
                        emit(RoadLayerData(true, message = roadServiceMessage(429)))
                        delay(wait)
                    }
                    val path = when (layer) {
                        RoadLayer.SPEED -> "api/v1/road-catalog"
                        RoadLayer.INCIDENTS -> "api/v1/road/traffic-incidents"
                        RoadLayer.SCHOOLS -> "api/v1/road/schools?lat=${point.lat}&lon=${point.lon}"
                        else -> "api/v1/road/emergency-pois?lat=${point.lat}&lon=${point.lon}&type=${layer.poiType}"
                    }
                    val result = try {
                        val response = api.request(base, path, session?.token, method = "GET")
                        if (response.code == 429) {
                            val seconds = response.retryAfter?.toLongOrNull()?.coerceIn(5L, 3600L) ?: 60L
                            cooldowns[key] = android.os.SystemClock.elapsedRealtime() + seconds * 1000L
                        }
                        if (response.code != 200) {
                            RoadLayerData(true, message = roadServiceMessage(response.code))
                        } else if (layer == RoadLayer.SPEED) {
                            val catalog = roadJson.decodeFromString<RoadCatalog>(response.body).validated()
                            RoadLayerData(true, catalog = catalog, message = when {
                                catalog.osmStale -> "OpenStreetMap · datos guardados"
                                catalog.osmAvailable -> "${catalog.speedSections.size} tramos · OpenStreetMap"
                                else -> "OpenStreetMap está temporalmente indisponible"
                            })
                        } else {
                            val (features, count) = roadFeatures(layer, response.body)
                            RoadLayerData(true, features, if (count == 0) "Sin resultados en esta zona" else "$count resultados · TomTom")
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        RoadLayerData(true, message = "No se pudo conectar. Revisa Internet y la URL del servidor en Ajustes")
                    }
                    emit(result)
                    if (result.message != roadServiceMessage(429)) {
                        delay(if (layer == RoadLayer.INCIDENTS) 60_000L else 300_000L)
                    }
                }
            }
        }
    }
}
