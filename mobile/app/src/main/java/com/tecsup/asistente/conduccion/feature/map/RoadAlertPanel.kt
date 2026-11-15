package com.tecsup.asistente.conduccion.feature.map

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private val catalogClient = OkHttpClient.Builder().callTimeout(22, TimeUnit.SECONDS).build()
private val catalogJson = Json { ignoreUnknownKeys = true }

@Composable
internal fun rememberRoadCatalog(url: String): State<RoadCatalog?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<RoadCatalog?>(null, lifecycle, url) {
        value = null
        if (url.isBlank()) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val catalog = withContext(Dispatchers.IO) {
                    runCatching {
                        val request = Request.Builder().url(url).build()
                        catalogClient.newCall(request).execute().use { response ->
                            check(response.isSuccessful)
                            val body = requireNotNull(response.body)
                            val source = body.source()
                            check(!source.request(8_000_001L))
                            catalogJson.decodeFromString<RoadCatalog>(source.readUtf8()).validated()
                        }
                    }.getOrNull()
                }
                value = catalog
                delay(60_000)
            }
        }
    }
}

@Composable
internal fun RoadAlertPanel(
    catalog: RoadCatalog,
    catalogLoaded: Boolean,
    position: RoadPoint?,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var voiceAvailable by remember { mutableStateOf<Boolean?>(null) }

    DisposableEffect(context, resumed) {
        if (!resumed) {
            onDispose { }
        } else {
            var active = true
            val main = Handler(Looper.getMainLooper())
            lateinit var instance: TextToSpeech
            instance = TextToSpeech(context) { status ->
                main.post {
                    if (active) {
                        voiceAvailable = status == TextToSpeech.SUCCESS &&
                            instance.setLanguage(Locale.forLanguageTag("es-PE")) >= TextToSpeech.LANG_AVAILABLE
                        if (voiceAvailable == true) engine = instance
                    }
                }
            }
            onDispose {
                active = false
                instance.stop()
                instance.shutdown()
                engine = null
                voiceAvailable = null
            }
        }
    }

    var lastPosition by remember { mutableStateOf<RoadPoint?>(null) }
    var headingOrigin by remember { mutableStateOf<RoadPoint?>(null) }
    LaunchedEffect(position) {
        val last = lastPosition
        if (position == null || last == null || position.distanceTo(last) > 150) {
            headingOrigin = null
            lastPosition = position
        } else if (position.distanceTo(last) >= 8) {
            headingOrigin = last
            lastPosition = position
        }
    }

    val alerts = if (position != null) catalog.alerts(position, headingOrigin) else emptyMap()
    val announced = remember { mutableMapOf<String, Long>() }
    LaunchedEffect(alerts, engine, resumed) {
        val speaker = engine ?: return@LaunchedEffect
        if (!resumed) return@LaunchedEffect
        val now = android.os.SystemClock.elapsedRealtime()
        announced.entries.removeAll { now - it.value >= 60_000 }
        alerts.forEach { (id, message) ->
            if (id !in announced) {
                if (speaker.speak(message, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.SUCCESS) {
                    announced[id] = now
                }
            }
        }
    }

    if (!catalogLoaded || !catalog.osmAvailable) Text("Datos viales de Arequipa no disponibles")
    if (!catalog.trafficAvailable) Text("Tráfico en vivo no disponible")
    if (voiceAvailable == false) Text("Voz en español no disponible en el dispositivo")
    alerts.values.forEach { Text(it) }
    if (position != null && alerts.keys.none { it.startsWith("speed:") }) Text("Límite de velocidad no disponible")
}

@Composable
internal fun rememberRoadTime(): State<Long> = produceState(Instant.now().epochSecond) {
    while (true) {
        value = Instant.now().epochSecond
        delay(1_000)
    }
}
