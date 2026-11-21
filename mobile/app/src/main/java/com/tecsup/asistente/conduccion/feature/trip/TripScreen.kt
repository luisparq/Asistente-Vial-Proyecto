package com.tecsup.asistente.conduccion.feature.trip

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ArchivoExpanded
import com.tecsup.asistente.conduccion.core.ui.AvigemColors
import com.tecsup.asistente.conduccion.core.ui.EventInfo
import com.tecsup.asistente.conduccion.core.ui.InfoCard
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.ReadError
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.StatusBadge
import com.tecsup.asistente.conduccion.core.ui.StatusTone
import com.tecsup.asistente.conduccion.core.ui.formatTimestamp
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.core.ui.rememberReducedMotion
import com.tecsup.asistente.conduccion.core.ui.tone
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType
import com.tecsup.asistente.conduccion.domain.model.TripWithEvents
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Recorrido activo: el asistente acompaña al conductor con estado, tiempo y resumen de maniobras. */
@Composable
fun TripScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    startedNew: Boolean = false,
    connection: BleConnectionState = BleConnectionState(),
    viewModel: TripViewModel = hiltViewModel(),
) {
    val state by viewModel.trip.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val maneuverAlertId by viewModel.maneuverAlertId.collectAsStateWithLifecycle()
    val sensorNotice by viewModel.sensorNotice.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(action.openHistory) {
        if (action.openHistory) {
            viewModel.navigationHandled()
            onFinished()
        }
    }
    TripVoice(greetOnStart = startedNew, maneuverAlertId = maneuverAlertId, onAlertDone = viewModel::dismissAlert)
    maneuverAlertId?.let { id ->
        com.tecsup.asistente.conduccion.feature.diagnostic.DemoAlertOverlay(
            "Maniobras bruscas", "", "", { viewModel.dismissAlert(id) },
            message = MANEUVER_ALERT_MESSAGE, demonstration = false,
        )
    }
    ScreenLayout(
        stringResource(R.string.trip_title), "", onBack, scrollable = false,
    ) {
        if (action.failed) Text(stringResource(R.string.write_error), color = MaterialTheme.colorScheme.error)
        when (val current = state) {
            LoadState.Loading -> CircularProgressIndicator()
            LoadState.Error -> ReadError()
            is LoadState.Ready -> {
                val detail = current.value
                if (detail == null) {
                    Text(stringResource(R.string.no_active_trip))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).testTag("trip_list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { TripHero(detail, connection) }
                        sensorNotice?.takeIf { it.tripId == detail.trip.id }?.let { notice ->
                            item {
                                val unit = if (notice.type == DrivingEventType.SHARP_TURN) "°/s" else "g"
                                val reading = String.format(Locale.getDefault(), "%.3f %s", notice.excess.value, unit)
                                val limit = String.format(Locale.getDefault(), "%.3f %s", notice.excess.limit, unit)
                                InfoCard {
                                    Text(stringResource(notice.type.labelResource()), style = MaterialTheme.typography.titleSmall)
                                    Text("Sensor · ${reading} · Límite ${limit}")
                                    Text(if (notice.buzzerSent) "Orden de buzzer enviada" else "No se pudo enviar la orden al buzzer")
                                }
                            }
                        }
                        item { Text(stringResource(R.string.event_count, detail.events.size), style = MaterialTheme.typography.titleSmall) }
                        if (detail.events.isEmpty()) {
                            item { Text(stringResource(R.string.active_no_events), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        items(detail.events, key = { "event-${it.id}" }) { event ->
                            InfoCard { EventInfo(event) }
                        }
                    }
                    Button(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirmFinish = true
                    }, enabled = !action.busy && !action.openHistory,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.finish_trip))
                    }
                }
            }
        }
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("¿Finalizar recorrido?") },
            text = { Text("Se guardará el resumen del viaje y se sincronizará cuando haya conexión.") },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; viewModel.finish() }) { Text("Finalizar") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Seguir conduciendo") } },
        )
    }
}

@Composable
private fun TripHero(detail: TripWithEvents, connection: BleConnectionState) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val seconds = ((now - detail.trip.startedAt.toEpochMilli()) / 1_000).coerceAtLeast(0)
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val clock = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600, seconds % 3_600 / 60, seconds % 60)
    Card(
        Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, AvigemColors.Citrine),
        colors = CardDefaults.cardColors(containerColor = AvigemColors.Graphite, contentColor = AvigemColors.Sillar100),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(stringResource(R.string.trip_active), StatusTone.PROGRESS)
                StatusBadge("AVIGEM One · " + stringResource(connection.status.labelResource()), connection.status.tone())
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(clock, style = if (largeText) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace, color = AvigemColors.Citrine)
                Text("Tiempo de recorrido · inicio " + formatTimestamp(detail.trip.startedAt),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val counters = listOf(
                "Frenadas" to DrivingEventType.HARSH_BRAKING,
                "Aceleraciones" to DrivingEventType.HARSH_ACCELERATION,
                "Giros" to DrivingEventType.SHARP_TURN,
            )
            if (largeText) {
                counters.forEach { (label, type) ->
                    ManeuverCounter(label, detail.events.count { it.type == type }, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    counters.forEach { (label, type) ->
                        ManeuverCounter(label, detail.events.count { it.type == type }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            val last = detail.events.maxByOrNull { it.occurredAt }
            Text(
                if (last == null) "Sin maniobras bruscas registradas. Sigue así."
                else "Última maniobra: " + stringResource(last.type.labelResource()) + " · " + formatTimestamp(last.occurredAt),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManeuverCounter(label: String, count: Int, modifier: Modifier = Modifier) {
    val visibleCount = if (rememberReducedMotion()) count else {
        val animated by animateFloatAsState(count.toFloat(), tween(200), label = "Conteo de maniobras")
        animated.roundToInt()
    }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = AvigemColors.Graphite700, contentColor = AvigemColors.Sillar100)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(visibleCount.toString(), style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(count.toString()) })
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
