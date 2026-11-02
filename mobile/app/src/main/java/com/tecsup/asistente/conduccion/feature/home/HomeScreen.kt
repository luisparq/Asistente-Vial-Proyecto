package com.tecsup.asistente.conduccion.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.tecsup.asistente.conduccion.core.ui.StatusBadge

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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TU ESPACIO DE CONDUCCIÓN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name?.takeIf { it.isNotBlank() }?.let { "Hola, $it" } ?: "Hola", style = MaterialTheme.typography.headlineLarge)
            company?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF252822), contentColor = Color(0xFFF7F4EB))) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TU RECORRIDO", style = MaterialTheme.typography.labelMedium, color = Color(0xFFCFD0C3))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color(0xFFF5B18C))
                }
                Text("Cada recorrido,\nun nuevo comienzo.", style = MaterialTheme.typography.headlineMedium)
                when (val current = active) {
                    LoadState.Loading -> CircularProgressIndicator(color = Color(0xFFF5B18C))
                    LoadState.Error -> ReadError()
                    is LoadState.Ready -> {
                        Text(stringResource(if (current.value == null) R.string.no_active_trip else R.string.trip_active), color = Color(0xFFCFD0C3), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = viewModel::startOrResume, enabled = !action.busy && !action.openTrip,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5B18C), contentColor = Color(0xFF382719))) {
                            Text(stringResource(if (current.value == null) R.string.start_trip else R.string.resume_trip))
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
                        }
                    }
                }
                if (action.failed) Text(stringResource(R.string.write_error), color = Color(0xFFFFB4AB))
                if (action.busy) CircularProgressIndicator(color = Color(0xFFF5B18C))
            }
        }
        Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Estado del asistente", style = MaterialTheme.typography.titleMedium)
                }
                StatusBadge(stringResource(connection.status.labelResource()))
                connection.device?.name?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Card(onClick = onMap, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Place, null)
                Text("Mi mapa", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
            }
        }
        Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeShortcut(stringResource(R.string.profile_title), Icons.Default.Person, onProfile, Modifier.weight(1f))
                HomeShortcut(stringResource(R.string.history_title), Icons.Default.DateRange, onHistory, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeShortcut(stringResource(R.string.ble_title), Icons.Default.Phone, onBle, Modifier.weight(1f))
                HomeShortcut(stringResource(R.string.settings_title), Icons.Default.Settings, onSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeShortcut(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 116.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
