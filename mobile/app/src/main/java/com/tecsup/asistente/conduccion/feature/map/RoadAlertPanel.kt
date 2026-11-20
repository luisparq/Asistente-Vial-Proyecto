package com.tecsup.asistente.conduccion.feature.map

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun RoadAlertPanel(
    catalog: RoadCatalog,
    catalogLoaded: Boolean,
    position: RoadPoint?,
    catalogMessage: String? = null,
    trafficMessage: String? = null,
    speak: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val resumed = speak && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
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
                        val peResult = instance.setLanguage(Locale.forLanguageTag("es-PE"))
                        val langOk = if (peResult >= TextToSpeech.LANG_AVAILABLE) true else {
                            instance.setLanguage(Locale("es", "419")) >= TextToSpeech.LANG_AVAILABLE ||
                            instance.setLanguage(Locale("es", "ES")) >= TextToSpeech.LANG_AVAILABLE ||
                            instance.setLanguage(Locale("es")) >= TextToSpeech.LANG_AVAILABLE
                        }
                        voiceAvailable = status == TextToSpeech.SUCCESS && langOk
                        if (voiceAvailable == true) {
                            try {
                                val spanishVoices = instance.voices?.filter { it.locale.language == "es" } ?: emptySet()
                                // Detecta voz masculina con palabra completa para no coincidir con "female".
                                val maleToken = Regex("(?:^|[-_])male(?:$|[-_])")
                                val maleVoice = spanishVoices.firstOrNull { voice ->
                                    val name = voice.name.lowercase(Locale.ROOT)
                                    val features = voice.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
                                    maleToken.containsMatchIn(name) ||
                                    features.any { maleToken.containsMatchIn(it) } ||
                                    name.contains("-eed-") || name.contains("-sfb-")
                                }
                                if (maleVoice != null) {
                                    instance.voice = maleVoice
                                }
                            } catch (_: Exception) { }
                            instance.setPitch(0.88f)
                            instance.setSpeechRate(0.98f)
                            engine = instance
                        }
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

    if (catalogMessage != null) Text(catalogMessage)
    else if (!catalogLoaded || !catalog.osmAvailable) Text("Datos viales de Arequipa no disponibles")
    if (trafficMessage != null) Text(trafficMessage)
    else if (!catalog.trafficAvailable) Text("Tráfico en vivo no disponible")
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
