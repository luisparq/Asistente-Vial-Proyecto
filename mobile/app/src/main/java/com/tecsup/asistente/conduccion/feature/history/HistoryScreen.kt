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
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.ReadError
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.TripInfo

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onTripSelected: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.trips.collectAsStateWithLifecycle()
    ScreenLayout(
        stringResource(R.string.history_title), stringResource(R.string.history_description),
        onBack, scrollable = false,
    ) {
        when (val current = state) {
            LoadState.Loading -> CircularProgressIndicator()
            LoadState.Error -> ReadError()
            is LoadState.Ready -> {
                if (current.value.isEmpty()) Text(stringResource(R.string.empty_history))
                LazyColumn(modifier = Modifier.weight(1f).testTag("history_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(current.value, key = { it.trip.id }) { summary ->
                        Card(onClick = { onTripSelected(summary.trip.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                TripInfo(summary.trip)
                                Text(stringResource(R.string.event_count, summary.eventCount))
                                Text(stringResource(R.string.view_events))
                            }
                        }
                    }
                }
            }
        }
    }
}
