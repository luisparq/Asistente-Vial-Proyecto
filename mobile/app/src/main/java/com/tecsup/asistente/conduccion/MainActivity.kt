package com.tecsup.asistente.conduccion

import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tecsup.asistente.conduccion.domain.ble.BleAvailability
import com.tecsup.asistente.conduccion.domain.repository.TripRepository
import com.tecsup.asistente.conduccion.feature.map.TripRoadVoiceService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tecsup.asistente.conduccion.core.ui.AsistenteVialTheme
import com.tecsup.asistente.conduccion.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject lateinit var ble: com.tecsup.asistente.conduccion.domain.ble.BleRepository
    @javax.inject.Inject lateinit var deviceMonitor: com.tecsup.asistente.conduccion.data.devices.DeviceMonitor
    @javax.inject.Inject lateinit var trips: TripRepository

    override fun onStart() { super.onStart(); ble.setForeground(true); ble.refresh(); deviceMonitor.setForeground(true) }
    override fun onStop() { deviceMonitor.setForeground(false); ble.setForeground(false); super.onStop() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(trips.observeActiveTrip(), ble.scan) { trip, scan ->
                    trip?.id?.takeIf { scan.availability == BleAvailability.READY }
                }.distinctUntilChanged().collect { tripId ->
                    if (tripId != null) {
                        try {
                            ContextCompat.startForegroundService(
                                this@MainActivity, Intent(this@MainActivity, TripRoadVoiceService::class.java),
                            )
                        } catch (error: IllegalStateException) {
                            Log.w("AVIGEM", "No se pudo iniciar el monitoreo vial", error)
                        } catch (error: SecurityException) {
                            Log.w("AVIGEM", "Permiso Bluetooth no disponible", error)
                        }
                    }
                }
            }
        }
        setContent {
            AsistenteVialTheme { AppNavHost() }
        }
    }
}
