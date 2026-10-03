package com.tecsup.asistente.conduccion.feature.diagnostic

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import com.tecsup.asistente.conduccion.core.ui.labelResource

@Composable
fun DiagnosticScreen(onBack: () -> Unit, viewModel: DiagnosticViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DiagnosticContent(state, onBack)
}

@Composable
fun DiagnosticContent(state: DiagnosticUiState, onBack: () -> Unit) {
    ScreenLayout(stringResource(R.string.diagnostic_title), stringResource(R.string.diagnostic_description), onBack) {
        Text(stringResource(state.connection.status.labelResource()))
        state.connection.device?.let {
            Text(it.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.ble_unnamed))
            Text(it.address)
        }
        Text(stringResource(when (state.sensorStatus) {
            SensorDataStatus.DISCONNECTED -> R.string.connection_disconnected
            SensorDataStatus.WAITING_FOR_DATA -> R.string.diagnostic_waiting
            SensorDataStatus.RECEIVING -> R.string.diagnostic_receiving
            SensorDataStatus.FAILED -> R.string.diagnostic_failed
        }))
        state.connection.error?.let { Text(it) }
        val sample = state.sample
        if (state.sensorStatus == SensorDataStatus.RECEIVING && sample != null) {
            Text("ax: ${sample.ax}")
            Text("ay: ${sample.ay}")
            Text("az: ${sample.az}")
            Text("gx: ${sample.gx}")
            Text("gy: ${sample.gy}")
            Text("gz: ${sample.gz}")
        } else Text(stringResource(R.string.diagnostic_no_measurements))
        Text(stringResource(R.string.diagnostic_raw_notice))
    }
}
