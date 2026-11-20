package com.tecsup.asistente.conduccion.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tecsup.asistente.conduccion.core.ui.rememberReducedMotion
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    excludedTripIds: Set<Long>? = emptySet(),
) {
    val reducedMotion = rememberReducedMotion()
    val state by viewModel.trips.collectAsStateWithLifecycle()
    ScreenLayout(
        stringResource(R.string.history_title), stringResource(R.string.history_description),
        onBack, scrollable = false, mainNav = true,
    ) {
        when (val current = state) {
            LoadState.Loading -> CircularProgressIndicator()
            LoadState.Error -> ReadError()
            is LoadState.Ready -> {
                val visible = if (excludedTripIds == null) emptyList() else current.value.filterNot { it.trip.id in excludedTripIds }
                if (excludedTripIds == null) CircularProgressIndicator()
                else if (visible.isEmpty()) Text(stringResource(R.string.empty_history))
                AnimatedVisibility(visible = visible.isNotEmpty(), modifier = Modifier.weight(1f),
                    enter = fadeIn(tween(if (reducedMotion) 0 else 180))) {
                LazyColumn(modifier = Modifier.testTag("history_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visible, key = { it.trip.id }) { summary ->
                        Card(onClick = { onTripSelected(summary.trip.id) }, modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth()
                                .background(Brush.horizontalGradient(listOf(Color(0xFF14263E), Color(0xFF1D304C))))
                                .padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                TripInfo(summary.trip)
                                summary.trip.endedAt?.let { ended ->
                                    val duration = java.time.Duration.between(summary.trip.startedAt, ended).seconds.coerceAtLeast(0)
                                    Text("Duración · ${duration / 60} min ${duration % 60} s")
                                }
                                Text(stringResource(R.string.event_count, summary.eventCount))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.view_events), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
