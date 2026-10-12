package com.tecsup.asistente.conduccion.feature.ble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.core.ui.PresentationSection
import com.tecsup.asistente.conduccion.core.ui.labelResource
import com.tecsup.asistente.conduccion.domain.ble.BleAvailability
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus
import com.tecsup.asistente.conduccion.domain.ble.ScanStatus

@Composable
fun BleContent(
    state: BleUiState,
    permissionDenied: Boolean,
    actionFailed: Boolean,
    onBack: () -> Unit,
    onPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onLocationSettings: () -> Unit,
    onAppSettings: () -> Unit,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSelect: (String) -> Unit,
    onPrepare: () -> Unit,
    onClear: () -> Unit,
    onDiagnostic: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onReconnect: () -> Unit = {},
) {
    ScreenLayout(stringResource(R.string.ble_title), stringResource(R.string.ble_description),
        onBack, scrollable = false) {
        LazyColumn(modifier = Modifier.weight(1f).testTag("ble_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                PresentationSection("Conexión del asistente") {
                    Text(if (state.scan.availability == BleAvailability.PERMISSION_REQUIRED) "Permisos requeridos"
                        else if (state.reconnection.running) "Reconectando"
                        else if (state.connection.status == ConnectionStatus.SELECTED && state.reconnection.message?.startsWith("Desconectado") == true) "Desconectado"
                        else stringResource(state.connection.status.labelResource()), style = MaterialTheme.typography.titleMedium)
                    state.connection.device?.let { device ->
                        Text(device.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ble_unnamed))
                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onPrepare, enabled = state.connection.status in listOf(
                            ConnectionStatus.SELECTED, ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED)) {
                            Text(stringResource(R.string.ble_prepare))
                        }
                        OutlinedButton(onClick = onDisconnect) { Text("Desconectar") }
                        OutlinedButton(onClick = onReconnect, enabled = !state.reconnection.running) { Text("Habilitar reconexión") }
                        OutlinedButton(onClick = onClear) { Text("Olvidar dispositivo / cambiar") }
                    }
                    state.reconnection.message?.let { Text(it) }
                    Text(if (state.reconnection.enabled) "Reconexión automática habilitada para esta cuenta." else "Reconexión automática suspendida.")
                    state.connection.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(stringResource(R.string.ble_not_verified))
                }
            }
            item {
                Text("Herramientas técnicas", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onDiagnostic) { Text(stringResource(R.string.diagnostic_title)) }
                Text("Consulta técnica de las mediciones del dispositivo.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                PresentationSection("Buscar dispositivos") {
                    val availabilityText = when (state.scan.availability) {
                        BleAvailability.CHECKING -> R.string.ble_checking
                        BleAvailability.UNSUPPORTED -> R.string.ble_unsupported
                        BleAvailability.PERMISSION_REQUIRED -> R.string.ble_permission_required
                        BleAvailability.BLUETOOTH_OFF -> R.string.ble_bluetooth_off
                        BleAvailability.LOCATION_OFF -> R.string.ble_location_off
                        BleAvailability.READY -> R.string.ble_ready
                        BleAvailability.UNAVAILABLE -> R.string.ble_unavailable
                    }
                    Text(stringResource(availabilityText))
                    when (state.scan.availability) {
                        BleAvailability.PERMISSION_REQUIRED -> {
                            Text(stringResource(R.string.ble_permission_explanation))
                            Button(onClick = onPermissions) { Text(stringResource(R.string.ble_grant_permissions)) }
                            if (permissionDenied) Text(stringResource(R.string.ble_permission_denied))
                            TextButton(onClick = onAppSettings) { Text(stringResource(R.string.ble_app_settings)) }
                        }
                        BleAvailability.BLUETOOTH_OFF ->
                            Button(onClick = onEnableBluetooth) { Text(stringResource(R.string.ble_enable_bluetooth)) }
                        BleAvailability.LOCATION_OFF ->
                            Button(onClick = onLocationSettings) { Text(stringResource(R.string.ble_location_settings)) }
                        else -> Unit
                    }
                    if (actionFailed) Text(stringResource(R.string.ble_action_failed), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.ble_refresh)) }
                    if (state.scan.status == ScanStatus.SCANNING) {
                        Text(stringResource(R.string.ble_scanning))
                        Button(onClick = onStop) { Text(stringResource(R.string.ble_stop)) }
                    } else {
                        Button(onClick = onStart, enabled = state.scan.availability == BleAvailability.READY) {
                            Text(stringResource(R.string.ble_start))
                        }
                        if (state.scan.status == ScanStatus.TIMED_OUT) Text(stringResource(R.string.ble_timed_out))
                        if (state.scan.status == ScanStatus.STOPPED) Text(stringResource(R.string.ble_stopped))
                    }
                    state.scan.failure?.let { failure ->
                        Text(stringResource(R.string.ble_scan_error), color = MaterialTheme.colorScheme.error)
                        failure.platformCode?.let { Text(stringResource(R.string.ble_platform_error, it)) }
                    }
                }
            }
            if (state.scan.devices.isEmpty()) item {
                Text(stringResource(if (state.scan.status == ScanStatus.IDLE) R.string.ble_no_scan else R.string.ble_no_devices))
            }
            items(state.scan.devices, key = { it.address }) { device ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(device.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ble_unnamed))
                        Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { onSelect(device.address) }) {
                            Text(stringResource(R.string.ble_select))
                        }
                    }
                }
            }
        }
    }
}
