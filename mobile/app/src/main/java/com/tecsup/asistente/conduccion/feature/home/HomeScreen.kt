package com.tecsup.asistente.conduccion.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
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
    onDiagnostic: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val active by viewModel.activeTrip.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    LaunchedEffect(action.openTrip) {
        if (action.openTrip) {
            viewModel.navigationHandled()
            onTrip()
        }
    }
    ScreenLayout(stringResource(R.string.home_title), stringResource(R.string.home_description)) {
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
        listOf(
            R.string.ble_title to onBle,
            R.string.diagnostic_title to onDiagnostic,
            R.string.history_title to onHistory,
            R.string.settings_title to onSettings,
        ).forEach { (label, action) ->
            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(label))
            }
        }
    }
}
