package com.tecsup.asistente.conduccion.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.AvigemColors
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.StatusBadge
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.core.ui.tone
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState

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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name?.takeIf { it.isNotBlank() }?.let { "Hola, $it" } ?: "Hola", style = MaterialTheme.typography.headlineLarge)
            company?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AvigemColors.Volcanic, contentColor = AvigemColors.Sillar100)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (val current = active) {
                    LoadState.Loading -> CircularProgressIndicator(color = AvigemColors.Citrine)
                    LoadState.Error -> Text(stringResource(R.string.read_error), color = AvigemColors.Sillar100)
                    is LoadState.Ready -> {
                        Text(stringResource(if (current.value == null) R.string.no_active_trip else R.string.trip_active), style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = viewModel::startOrResume, enabled = !action.busy && !action.openTrip,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = AvigemColors.Citrine, contentColor = AvigemColors.Volcanic,
                                disabledContainerColor = AvigemColors.Graphite700, disabledContentColor = AvigemColors.Mineral400)) {
                            Text(stringResource(if (current.value == null) R.string.start_trip else R.string.resume_trip), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
                        }
                    }
                }
                if (action.failed) Text(stringResource(R.string.write_error), color = AvigemColors.Sillar100)
                if (action.busy) CircularProgressIndicator(color = AvigemColors.Citrine)
            }
        }
        Card(onClick = onBle, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AVIGEM One", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ble_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(stringResource(connection.status.labelResource()), connection.status.tone())
                connection.device?.name?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeShortcut("Mi mapa", Icons.Default.Place, onMap, Modifier.weight(1f))
                HomeShortcut(stringResource(R.string.history_title), Icons.Default.DateRange, onHistory, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeShortcut(stringResource(R.string.profile_title), Icons.Default.Person, onProfile, Modifier.weight(1f))
                HomeShortcut(stringResource(R.string.settings_title), Icons.Default.Settings, onSettings, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeShortcut(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 64.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
