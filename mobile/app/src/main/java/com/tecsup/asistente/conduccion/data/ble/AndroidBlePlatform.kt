package com.tecsup.asistente.conduccion.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.tecsup.asistente.conduccion.core.permissions.BlePermissionPolicy
import com.tecsup.asistente.conduccion.domain.ble.BleAvailability
import com.tecsup.asistente.conduccion.domain.ble.BleDevice
import com.tecsup.asistente.conduccion.domain.ble.BlePlatform
import com.tecsup.asistente.conduccion.domain.ble.ScanFailure
import com.tecsup.asistente.conduccion.domain.ble.ScanFailureReason
import com.tecsup.asistente.conduccion.domain.ble.ScanSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidBlePlatform @Inject constructor(
    @ApplicationContext private val context: Context,
) : BlePlatform {
    private fun permissionsGranted() = BlePermissionPolicy.runtimePermissions(Build.VERSION.SDK_INT)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    // Lint no reconoce la comprobación agrupada anterior. Se comprueba antes de
    // consultar el adaptador y se captura revocación concurrente de permisos.
    @SuppressLint("MissingPermission")
    override fun availability(): BleAvailability {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BleAvailability.UNSUPPORTED
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return BleAvailability.UNSUPPORTED
        if (!permissionsGranted()) return BleAvailability.PERMISSION_REQUIRED
        return try {
            when {
                !adapter.isEnabled -> BleAvailability.BLUETOOTH_OFF
                BlePermissionPolicy.requiresLocationService(Build.VERSION.SDK_INT) &&
                    context.getSystemService(LocationManager::class.java)?.let {
                        !LocationManagerCompat.isLocationEnabled(it)
                    } != false -> BleAvailability.LOCATION_OFF
                else -> BleAvailability.READY
            }
        } catch (_: SecurityException) {
            BleAvailability.PERMISSION_REQUIRED
        } catch (_: IllegalStateException) {
            BleAvailability.UNAVAILABLE
        }
    }

    override fun observeAvailability() = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Reconsultar el sistema: nunca confiar en extras recibidos.
                trySend(availability())
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        // Bluetooth puede emitir desde un proceso privilegiado distinto del UID
        // del sistema. Solo se escuchan cambios y se reconsulta el estado real.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        trySend(availability())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    @SuppressLint("MissingPermission")
    override fun startScan(
        onDevice: (BleDevice) -> Unit,
        onFailure: (ScanFailure) -> Unit,
    ): ScanSession {
        val available = availability()
        if (available != BleAvailability.READY) {
            onFailure(ScanFailure(
                if (available == BleAvailability.PERMISSION_REQUIRED) ScanFailureReason.PERMISSION
                else ScanFailureReason.BLUETOOTH_UNAVAILABLE,
            ))
            return ScanSession {}
        }
        return try {
            val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
            if (scanner == null) {
                onFailure(ScanFailure(ScanFailureReason.BLUETOOTH_UNAVAILABLE))
                return ScanSession {}
            }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    if (!permissionsGranted()) {
                        onFailure(ScanFailure(ScanFailureReason.PERMISSION))
                        return
                    }
                    try {
                        onDevice(BleDevice(result.device.address, result.scanRecord?.deviceName ?: result.device.name))
                    } catch (_: SecurityException) {
                        onFailure(ScanFailure(ScanFailureReason.PERMISSION))
                    }
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    onFailure(ScanFailure(ScanFailureReason.PLATFORM, errorCode))
                }
            }
            // Se conservan los anuncios cercanos; el perfil esperado se valida al conectar.
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            ScanSession {
                try {
                    if (Build.VERSION.SDK_INT < 31 ||
                        ContextCompat.checkSelfPermission(context, BlePermissionPolicy.SCAN) == PackageManager.PERMISSION_GRANTED) {
                        scanner.stopScan(callback)
                    }
                } catch (_: SecurityException) {
                    // El permiso pudo revocarse entre la comprobación y stopScan.
                } catch (_: IllegalStateException) {
                    // El adaptador puede apagarse antes de procesar la parada.
                }
            }
        } catch (_: SecurityException) {
            onFailure(ScanFailure(ScanFailureReason.PERMISSION))
            ScanSession {}
        } catch (_: IllegalStateException) {
            onFailure(ScanFailure(ScanFailureReason.BLUETOOTH_UNAVAILABLE))
            ScanSession {}
        }
    }
}
