package com.tecsup.asistente.conduccion.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState
import com.tecsup.asistente.conduccion.core.ui.labelResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.ReadError
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout

@Composable
fun HomeScreen(
    onTrip: () -> Unit,
    onBle: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onMap: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    name: String? = null,
    company: String? = null,
    connection: BleConnectionState = BleConnectionState(),
) {
    val active by viewModel.activeTrip.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    LaunchedEffect(action.openTrip) {
        if (action.openTrip) {
            viewModel.navigationHandled()
            onTrip()
        }
    }
    ScreenLayout(stringResource(R.string.home_title), "") {
        Text(name?.takeIf { it.isNotBlank() }?.let { "Hola, $it" } ?: "Hola", style = MaterialTheme.typography.headlineMedium)
        company?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
          Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tu recorrido", style = MaterialTheme.typography.titleLarge)
        when (val current = active) {
            LoadState.Loading -> CircularProgressIndicator()
            LoadState.Error -> ReadError()
            is LoadState.Ready -> {
                Text(stringResource(if (current.value == null) R.string.no_active_trip else R.string.trip_active))
                Button(onClick = viewModel::startOrResume, enabled = !action.busy && !action.openTrip,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (current.value == null) R.string.start_trip else R.string.resume_trip))
                }
            }
        }
        if (action.failed) Text(stringResource(R.string.write_error), color = MaterialTheme.colorScheme.error)
        if (action.busy) CircularProgressIndicator()
          }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado del asistente", style = MaterialTheme.typography.titleMedium)
                Text(stringResource(connection.status.labelResource()))
                connection.device?.name?.takeIf { it.isNotBlank() }?.let { Text(it) }
            }
        }
        OutlinedButton(onClick = onMap, modifier = Modifier.fillMaxWidth()) { Text("Mi mapa") }
        Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
        listOf(
            Triple(R.string.profile_title, Icons.Default.Person, onProfile),
            Triple(R.string.history_title, Icons.Default.DateRange, onHistory),
            Triple(R.string.ble_title, Icons.Default.Phone, onBle),
            Triple(R.string.settings_title, Icons.Default.Settings, onSettings),
        ).forEach { (label, icon, action) ->
            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(label))
            }
        }
    }
}
