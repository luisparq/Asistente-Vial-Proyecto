package com.tecsup.asistente.conduccion.feature.trip

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private const val START_GREETING =
    "Hola, soy Abigem. Te acompañaré durante tu recorrido."
internal const val MANEUVER_ALERT_MESSAGE =
    "Se registraron varias maniobras bruscas. Conduce con precaución"
private const val START_GREETING_UTTERANCE = "start_trip_greeting"

/**
 * Saludo hablado del recorrido recién creado.
 *
 * El motor vive mientras la pantalla del recorrido está compuesta y se libera al salir.
 * [greetOnStart] solo es verdadero cuando esta navegación abrió un recorrido nuevo; retomar,
 * recomponer o rotar no repite el saludo. Si no hay motor o voz en español, no se
 * reproduce nada y el recorrido continúa con normalidad.
 */
@Composable
internal fun TripVoice(greetOnStart: Boolean, maneuverAlertId: String?) {
    if (!greetOnStart && maneuverAlertId == null) return
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var greeted by rememberSaveable { mutableStateOf(false) }
    var spokenAlertId by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        var active = true
        val main = Handler(Looper.getMainLooper())
        lateinit var instance: TextToSpeech
        val created = runCatching {
            instance = TextToSpeech(context) { status ->
                main.post {
                    if (!active) return@post
                    if (status == TextToSpeech.SUCCESS && instance.selectSpanishVoice()) {
                        instance.setPitch(0.88f)
                        instance.setSpeechRate(0.98f)
                        engine = instance
                    } else {
                        instance.shutdown()
                    }
                }
            }
        }.isSuccess
        onDispose {
            active = false
            if (created) {
                instance.stop()
                instance.shutdown()
            }
            engine = null
        }
    }

    LaunchedEffect(engine, greetOnStart, maneuverAlertId) {
        val speaker = engine ?: return@LaunchedEffect
        if (greetOnStart && !greeted) {
            greeted = true
            speaker.speak(START_GREETING, TextToSpeech.QUEUE_ADD, null, START_GREETING_UTTERANCE)
        }
        if (maneuverAlertId != null && spokenAlertId != maneuverAlertId &&
            speaker.selectSpanishVoice(offlineOnly = true) &&
            speaker.speak(MANEUVER_ALERT_MESSAGE, TextToSpeech.QUEUE_ADD, null, maneuverAlertId) == TextToSpeech.SUCCESS
        ) spokenAlertId = maneuverAlertId
    }
}

/**
 * Elige una voz en español preferiblemente masculina.
 *
 * La búsqueda de voz masculina usa coincidencia exacta de la palabra «male» para evitar
 * que «female» sea seleccionada erróneamente (cualquier substring «male» incluye «female»).
 *
 * Prioridad: masculina es-PE → masculina en español → cualquier es-PE → cualquier español.
 * Si ninguna voz es aplicable, cae al setLanguage con locales de fallback.
 */
private fun TextToSpeech.selectSpanishVoice(offlineOnly: Boolean = false): Boolean {
    val spanishVoices = runCatching {
        voices?.filter { it.locale.language == "es" && (!offlineOnly || !it.isNetworkConnectionRequired) }.orEmpty()
    }.getOrDefault(emptyList())

    // Detecta voz masculina: el nombre o los features deben contener "male" como palabra
    // completa (delimitada por inicio, fin, guion o guion bajo) para no coincidir con "female".
    fun isMale(voice: android.speech.tts.Voice): Boolean {
        val name = voice.name.lowercase(Locale.ROOT)
        val features = voice.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
        val maleToken = Regex("(?:^|[-_])male(?:$|[-_])")
        return maleToken.containsMatchIn(name) ||
            features.any { maleToken.containsMatchIn(it) } ||
            name.contains("-eed-") || name.contains("-sfb-")
    }

    val maleVoices = spanishVoices.filter(::isMale)
    val preferred = maleVoices.firstOrNull { it.locale == Locale.forLanguageTag("es-PE") }
        ?: maleVoices.firstOrNull()
        ?: spanishVoices.firstOrNull { it.locale == Locale.forLanguageTag("es-PE") }
        ?: spanishVoices.firstOrNull()

    if (preferred != null) {
        val applied = runCatching { setVoice(preferred) }
            .getOrDefault(TextToSpeech.ERROR)
        if (applied == TextToSpeech.SUCCESS) return true
    }
    if (offlineOnly) return false
    val fallbacks = listOf(
        Locale.forLanguageTag("es-PE"),
        Locale.forLanguageTag("es-419"),
        Locale("es", "ES"),
        Locale.forLanguageTag("es"),
    )
    for (locale in fallbacks) {
        val available = runCatching { isLanguageAvailable(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (available >= TextToSpeech.LANG_AVAILABLE) {
            val applied = runCatching { setLanguage(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            return applied >= TextToSpeech.LANG_AVAILABLE
        }
    }
    return false
}
