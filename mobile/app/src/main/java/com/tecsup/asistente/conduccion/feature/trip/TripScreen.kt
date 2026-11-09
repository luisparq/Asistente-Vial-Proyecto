package com.tecsup.asistente.conduccion.feature.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.EventInfo
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.ReadError
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.TripInfo
import com.tecsup.asistente.conduccion.core.ui.InfoCard
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.domain.model.DrivingEventType

@Composable
fun TripScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: TripViewModel = hiltViewModel(),
) {
    val state by viewModel.trip.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    LaunchedEffect(action.openHistory) {
        if (action.openHistory) {
            viewModel.navigationHandled()
            onFinished()
        }
    }
    ScreenLayout(
        stringResource(R.string.trip_title), stringResource(R.string.test_data_notice),
        onBack, scrollable = false,
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
                    Text(stringResource(R.string.event_count, detail.events.size))
                    LazyColumn(modifier = Modifier.weight(1f).testTag("trip_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            InfoCard {
                                TripInfo(detail.trip)
                                Text(stringResource(R.string.active_survives_close), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        item {
                            Button(onClick = viewModel::finish, enabled = !action.busy && !action.openHistory,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = MaterialTheme.shapes.small) {
                                Text(stringResource(R.string.finish_trip))
                            }
                        }
                        items(DrivingEventType.entries) { type ->
                            OutlinedButton(onClick = { viewModel.simulate(type) },
                                enabled = !action.busy && !action.openHistory, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.simulate_event, stringResource(type.labelResource())))
                            }
                        }
                        if (detail.events.isEmpty()) item { Text(stringResource(R.string.no_events)) }
                        items(detail.events, key = { "event-${it.id}" }) { event ->
                            InfoCard { EventInfo(event) }
                        }
                    }
                }
            }
        }
    }
}
