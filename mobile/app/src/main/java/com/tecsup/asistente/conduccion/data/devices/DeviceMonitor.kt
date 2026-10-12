package com.tecsup.asistente.conduccion.data.devices

import com.tecsup.asistente.conduccion.core.di.BleScope
import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataSource
import com.tecsup.asistente.conduccion.domain.sync.*
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

@Singleton class DeviceMonitor @Inject constructor(private val ble: BleRepository,
    private val samples: SensorDataSource, private val remembered: RememberedBleStore,
    private val sessions: SessionStore, private val sync: DeviceReportSync,
    private val scheduler: SyncScheduler, private val clock: Clock, @BleScope private val scope: CoroutineScope) {
    private var job: Job? = null
    fun setForeground(active: Boolean) {
        if (!active) { job?.cancel(); job=null; return }
        if (job?.isActive == true) return
        job=scope.launch {
            while (isActive) {
                try {
                    val session=sessions.state.value
                    val device=session?.let { remembered.read(it.ownerKey) }
                    if (device != null && session.token != null) {
                        val connection=ble.connection.value
                        val selected=connection.device?.address == device.address
                        val availability=ble.scan.value.availability
                        val state=when {
                            availability == BleAvailability.PERMISSION_REQUIRED -> "PERMISSION_REQUIRED"
                            availability == BleAvailability.BLUETOOTH_OFF -> "BLUETOOTH_OFF"
                            ble.reconnection.value.running -> "RECONNECTING"
                            selected -> connection.status.name
                            else -> "DISCONNECTED"
                        }
                        val now=clock.millis()
                        val report=DeviceReport(device.name ?: "Dispositivo BLE",Contract.timestamp(now),
                            if(selected) DeviceReportPolicy.contact(now,samples.sample.value?.receivedAtMillis,samples.gps.value.receivedAtMillis,null) else null,
                            state,if(selected) samples.status.value.name else "DISCONNECTED",
                            if(selected) samples.gps.value.status.name else "DISCONNECTED",
                            connection.error?.takeIf { selected }?.take(255))
                        if(sync.capture(session.ownerKey,device.address,report)) scheduler.scheduleFallback()
                        if(sync.flush()) scheduler.scheduleFallback()
                    }
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) { scheduler.scheduleFallback() }
                delay(5_000)
            }
        }
    }
}
