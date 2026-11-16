package com.tecsup.asistente.conduccion.feature.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.EventInfo
import com.tecsup.asistente.conduccion.core.ui.InfoCard
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.ReadError
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.TripInfo
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType

@Composable
fun TripScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    startedNew: Boolean = false,
    viewModel: TripViewModel = hiltViewModel(),
) {
    val state by viewModel.trip.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val maneuverAlertId by viewModel.maneuverAlertId.collectAsStateWithLifecycle()
    var showTestControls by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(action.openHistory) {
        if (action.openHistory) {
            viewModel.navigationHandled()
            onFinished()
        }
    }
    // El saludo pertenece a esta pantalla: el motor de voz se libera al salir del recorrido.
    TripVoice(greetOnStart = startedNew, maneuverAlertId = maneuverAlertId)
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
                    InfoCard { TripInfo(detail.trip) }
                    if (maneuverAlertId != null) {
                        InfoCard { Text("Prueba · $MANEUVER_ALERT_MESSAGE") }
                    }
                    Text(stringResource(R.string.event_count, detail.events.size))
                    LazyColumn(
                        modifier = Modifier.weight(1f).testTag("trip_list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            TextButton(onClick = { showTestControls = !showTestControls }) {
                                Text(if (showTestControls) "Ocultar prueba de maniobras" else "Probar alerta de maniobras")
                            }
                            if (showTestControls) {
                                Text("Registra maniobras simuladas durante este recorrido.")
                                listOf(
                                    DrivingEventType.SHARP_TURN to "Simular giro",
                                    DrivingEventType.HARSH_BRAKING to "Simular frenada",
                                    DrivingEventType.HARSH_ACCELERATION to "Simular aceleración",
                                ).forEach { (type, label) ->
                                    TextButton(
                                        onClick = { viewModel.simulateManeuver(type) },
                                        enabled = !action.busy && !action.openHistory,
                                    ) { Text(label) }
                                }
                            }
                        }
                        if (detail.events.isEmpty()) {
                            item { Text(stringResource(R.string.active_no_events)) }
                        }
                        items(detail.events, key = { "event-${it.id}" }) { event ->
                            InfoCard { EventInfo(event) }
                        }
                    }
                    Button(onClick = viewModel::finish, enabled = !action.busy && !action.openHistory,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.finish_trip))
                    }
                }
            }
        }
    }
}
