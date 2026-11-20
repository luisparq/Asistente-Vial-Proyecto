package com.tecsup.asistente.conduccion.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.AvigemColors
import com.tecsup.asistente.conduccion.core.ui.AvigemTile
import com.tecsup.asistente.conduccion.core.ui.LoadState
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.StatusBadge
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.core.ui.tone
import com.tecsup.asistente.conduccion.domain.ble.BleConnectionState
import com.tecsup.asistente.conduccion.feature.map.DriverMapPreview

private val HomeSlate = Color(0xFF233245)

@Composable
fun HomeScreen(
    onTrip: (startedNew: Boolean) -> Unit,
    onBle: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onMap: () -> Unit = {},
    onPresentation: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    name: String? = null,
    company: String? = null,
    connection: BleConnectionState = BleConnectionState(),
) {
    val active by viewModel.activeTrip.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()

    LaunchedEffect(action.openTrip) {
        if (action.openTrip) {
            val startedNew = action.startedNewTrip
            viewModel.navigationHandled()
            onTrip(startedNew)
        }
    }
    ScreenLayout(stringResource(R.string.home_title), "", mainNav = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvigemTile(size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val firstName = name?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
                Text(firstName?.let { "Hola, $it" } ?: "Hola", style = MaterialTheme.typography.headlineMedium)
                company?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledIconButton(
                onClick = onSettings,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = HomeSlate,
                    contentColor = AvigemColors.Sillar100,
                ),
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
            }
        }
        Card(onClick = onBle, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = AvigemColors.Sillar100, contentColor = AvigemColors.Volcanic,
        )) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvigemTile(size = 30.dp)
                    Text("AVIGEM One", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ble_title), style = MaterialTheme.typography.labelMedium, color = AvigemColors.Mineral600)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = AvigemColors.Mineral600)
                }
                StatusBadge(stringResource(connection.status.labelResource()), connection.status.tone())
                if (connection.device == null) Text("Buscar dispositivos", style = MaterialTheme.typography.labelLarge)
                connection.device?.name?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AvigemColors.Mineral600) }
            }
        }
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AvigemColors.Sillar100, contentColor = AvigemColors.Volcanic),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Mi mapa", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(
                    onClick = onMap,
                    colors = ButtonDefaults.textButtonColors(contentColor = AvigemColors.Volcanic),
                ) {
                    Text("Abrir mapa")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp))
                }
            }
            DriverMapPreview(Modifier.fillMaxWidth().height(218.dp))
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = HomeSlate, contentColor = AvigemColors.Sillar100)) {
            Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(HomeSlate, Color(0xFF14233B))))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val current = active) {
                    LoadState.Loading -> CircularProgressIndicator(color = AvigemColors.Citrine)
                    LoadState.Error -> Text(stringResource(R.string.read_error), color = AvigemColors.Sillar100)
                    is LoadState.Ready -> {
                        Text(stringResource(if (current.value == null) R.string.no_active_trip else R.string.trip_active), style = MaterialTheme.typography.titleMedium)
                        Button(onClick = viewModel::startOrResume, enabled = !action.busy && !action.openTrip,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = AvigemColors.Sillar100, contentColor = AvigemColors.Volcanic,
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


    }
}
