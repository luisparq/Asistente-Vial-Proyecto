package com.tecsup.asistente.conduccion.data.ble

import com.tecsup.asistente.conduccion.core.di.BleScope
import com.tecsup.asistente.conduccion.domain.ble.BleAvailability
import com.tecsup.asistente.conduccion.domain.ble.BleConnection
import com.tecsup.asistente.conduccion.domain.ble.BlePlatform
import com.tecsup.asistente.conduccion.domain.ble.BleRepository
import com.tecsup.asistente.conduccion.domain.ble.BleScanState
import com.tecsup.asistente.conduccion.domain.ble.ScanFailure
import com.tecsup.asistente.conduccion.domain.ble.ScanFailureReason
import com.tecsup.asistente.conduccion.domain.ble.ScanSession
import com.tecsup.asistente.conduccion.domain.ble.ScanStatus
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class DefaultBleRepository @Inject constructor(
    private val platform: BlePlatform,
    private val connector: BleConnection,
    @BleScope private val scope: CoroutineScope,
    private val reconnect: BleReconnectController? = null,
) : BleRepository {
    private val mutableScan = MutableStateFlow(BleScanState())
    override val scan = mutableScan.asStateFlow()
    override val connection = connector.state
    override val buzzerDone = connector.buzzerDone
    override fun triggerBuzzer(): Boolean = connector.triggerBuzzer()
    override val reconnection = reconnect?.state ?: com.tecsup.asistente.conduccion.domain.ble.noBleReconnect
    override fun setForeground(value: Boolean) { reconnect?.foreground(value) }
    override fun enableReconnection() { reconnect?.enable() }
    override fun disconnectManual() { if (reconnect != null) reconnect.disconnect() else connector.clear() }
    private var session: ScanSession? = null
    private var timeout: Job? = null
    private var generation = 0L

    init {
        scope.launch {
            platform.observeAvailability().catch { emit(BleAvailability.UNAVAILABLE) }
                .collect { updateAvailability(it) }
        }
    }

    override fun refresh() { scope.launch { updateAvailability(platform.availability()); reconnect?.refresh() } }

    private fun updateAvailability(value: BleAvailability) {
        mutableScan.value = scan.value.copy(availability = value)
        if (value != BleAvailability.READY && scan.value.status == ScanStatus.SCANNING) {
            endScan(ScanStatus.BLOCKED)
        }
    }

    override fun startScan() {
        scope.launch {
            updateAvailability(platform.availability())
            if (scan.value.status == ScanStatus.SCANNING) return@launch
            if (scan.value.availability != BleAvailability.READY) {
                mutableScan.value = scan.value.copy(status = ScanStatus.BLOCKED, failure = null)
                return@launch
            }
            val token = ++generation
            mutableScan.value = scan.value.copy(status = ScanStatus.SCANNING, devices = emptyList(), failure = null)
            val newSession = platform.startScan(
                onDevice = { device ->
                    scope.launch {
                        if (token == generation && scan.value.status == ScanStatus.SCANNING) {
                            val devices = scan.value.devices.associateBy { it.address }.toMutableMap()
                            val previous = devices[device.address]
                            devices[device.address] = device.copy(name = device.name ?: previous?.name)
                            mutableScan.value = scan.value.copy(devices = devices.values.toList())
                        }
                    }
                },
                onFailure = { failure ->
                    scope.launch {
                        if (token == generation) {
                            endScan(ScanStatus.FAILED, failure)
                            if (failure.reason == ScanFailureReason.PERMISSION) {
                                updateAvailability(BleAvailability.PERMISSION_REQUIRED)
                            }
                        }
                    }
                },
            )
            // Algunos fallos son síncronos; no conservar la sesión ya invalidada.
            if (token != generation) {
                newSession.stop()
                return@launch
            }
            session = newSession
            timeout = scope.launch {
                delay(SCAN_DURATION_MILLIS)
                if (token == generation) endScan(ScanStatus.TIMED_OUT)
            }
        }
    }

    override fun stopScan() {
        scope.launch { if (scan.value.status == ScanStatus.SCANNING) endScan(ScanStatus.STOPPED) }
    }

    private fun endScan(status: ScanStatus, failure: ScanFailure? = null) {
        generation++
        timeout?.cancel()
        timeout = null
        session?.stop()
        session = null
        mutableScan.value = scan.value.copy(status = status, failure = failure)
    }

    override fun selectDevice(address: String) {
        scope.launch {
            val device = scan.value.devices.firstOrNull { it.address == address } ?: return@launch
            if (scan.value.status == ScanStatus.SCANNING) endScan(ScanStatus.STOPPED)
            if (reconnect != null) reconnect.select(device) else connector.select(device)
        }
    }

    override fun prepareConnection() {
        scope.launch {
            if (scan.value.status == ScanStatus.SCANNING) endScan(ScanStatus.STOPPED)
            // The connector checks selection after previously queued selection commands.
            if (reconnect != null) reconnect.connectSelected() else connector.prepare()
        }
    }
    override fun clearSelection() { scope.launch { if (reconnect != null) reconnect.forget() else connector.clear() } }

    companion object { const val SCAN_DURATION_MILLIS = 10_000L }
}
