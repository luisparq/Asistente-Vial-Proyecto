package com.tecsup.asistente.conduccion.feature.ble

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tecsup.asistente.conduccion.core.permissions.BlePermissionPolicy

@Composable
fun BleScreen(onBack: () -> Unit, viewModel: BleViewModel = hiltViewModel(), onDiagnostic: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var actionFailed by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionDenied = result.values.any { !it }
        viewModel.refresh()
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }
    fun launchSettings(intent: Intent) {
        actionFailed = false
        try {
            settings.launch(intent)
        } catch (_: ActivityNotFoundException) {
            actionFailed = true
        } catch (_: SecurityException) {
            actionFailed = true
            viewModel.refresh()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopScan() }
    DisposableEffect(viewModel) { onDispose { viewModel.stopScan() } }

    BleContent(
        state = state, permissionDenied = permissionDenied, actionFailed = actionFailed, onBack = onBack,
        onPermissions = {
            permissions.launch(BlePermissionPolicy.runtimePermissions(Build.VERSION.SDK_INT).toTypedArray())
        },
        onEnableBluetooth = { launchSettings(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        onLocationSettings = { launchSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onAppSettings = { launchSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null))) },
        onRefresh = viewModel::refresh, onStart = viewModel::startScan, onStop = viewModel::stopScan,
        onSelect = viewModel::select, onPrepare = viewModel::prepareConnection, onClear = viewModel::clearSelection,
        onDiagnostic = onDiagnostic,
    )
}
