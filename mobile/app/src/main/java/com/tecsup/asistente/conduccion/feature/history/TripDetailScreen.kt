package com.tecsup.asistente.conduccion.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun TripDetailScreen(onBack: () -> Unit, viewModel: TripDetailViewModel = hiltViewModel()) {
    val state by viewModel.trip.collectAsStateWithLifecycle()
    ScreenLayout(
        stringResource(R.string.trip_detail_title),
        stringResource(R.string.test_data_notice), onBack, scrollable = false,
    ) {
        when (val current = state) {
            LoadState.Loading -> CircularProgressIndicator()
            LoadState.Error -> ReadError()
            is LoadState.Ready -> {
                val detail = current.value
                if (detail == null) {
                    Text(stringResource(R.string.trip_not_found))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).testTag("events_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { TripInfo(detail.trip) } }
                        item { Text(stringResource(R.string.event_count, detail.events.size)) }
                        if (detail.events.isEmpty()) {
                            item { Text(stringResource(R.string.no_events)) }
                        }
                        items(detail.events, key = { it.id }) { event ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    EventInfo(event)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
