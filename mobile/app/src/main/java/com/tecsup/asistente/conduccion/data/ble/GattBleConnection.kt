package com.tecsup.asistente.conduccion.data.ble

import com.tecsup.asistente.conduccion.core.di.BleScope
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.SensorDataStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Singleton
class GattBleConnection @Inject constructor(
    private val transport: GattTransport,
    private val source: RawSensorDataSource,
    private val platform: BlePlatform,
    @BleScope private val scope: CoroutineScope,
) : BleConnection {
    private val mutableState = MutableStateFlow(BleConnectionState())
    override val state = mutableState.asStateFlow()
    private var session: GattTransport.Session? = null
    private var generation = 0L
    private var timeout: Job? = null
    private enum class Phase { IDLE, LINK, SERVICES, CCCD, STREAMING }
    private var phase = Phase.IDLE

    init {
        scope.launch {
            platform.observeAvailability().catch { emit(BleAvailability.UNAVAILABLE) }.collect {
                if (phase != Phase.IDLE && it in listOf(BleAvailability.BLUETOOTH_OFF,
                        BleAvailability.PERMISSION_REQUIRED, BleAvailability.UNSUPPORTED, BleAvailability.UNAVAILABLE)) {
                    fail("Bluetooth o sus permisos dejaron de estar disponibles. Revisa los ajustes y vuelve a conectar.")
                }
            }
        }
    }

    override fun select(device: BleDevice) { scope.launch {
        release()
        source.reset(SensorDataStatus.DISCONNECTED)
        mutableState.value = BleConnectionState(ConnectionStatus.SELECTED, device)
    } }

    override fun clear() { scope.launch {
        release()
        source.reset(SensorDataStatus.DISCONNECTED)
        mutableState.value = BleConnectionState()
    } }

    override fun prepare() { scope.launch {
        val device = state.value.device ?: return@launch
        if (phase != Phase.IDLE) return@launch
        release()
        val token = generation
        phase = Phase.LINK
        mutableState.value = BleConnectionState(ConnectionStatus.CONNECTING, device)
        source.reset(SensorDataStatus.WAITING_FOR_DATA)
        timeout = scope.launch {
            delay(CONNECT_TIMEOUT_MILLIS)
            if (token == generation) fail("Tiempo de conexión agotado. Acerca el ESP32 y vuelve a conectar.")
        }
        try {
            session = transport.connect(device.address, object : GattTransport.Listener {
                // yield also handles synchronous failures from a transport before connect returns.
                private fun event(action: () -> Unit) { scope.launch {
                    yield()
                    if (token == generation) {
                        try { action() } catch (_: SecurityException) { fail("Falta el permiso de conexión Bluetooth.") }
                        catch (_: IllegalStateException) { fail("Android no pudo completar la operación GATT.") }
                    }
                } }
                override fun connected() = event {
                    if (phase == Phase.LINK) {
                        phase = Phase.SERVICES
                        if (session?.discover() != true) fail("No se pudo iniciar el descubrimiento de servicios.")
                    }
                }
                override fun services(profile: GattProfileDescription) = event {
                    if (phase == Phase.SERVICES) {
                        val error = profile.error()
                        if (error != null) fail(error) else {
                            phase = Phase.CCCD
                            if (session?.subscribe() != true) fail("No se pudo solicitar la suscripción a IMU_RAW.")
                        }
                    }
                }
                override fun subscribed() = event {
                    if (phase == Phase.CCCD) {
                        timeout?.cancel()
                        phase = Phase.STREAMING
                        mutableState.value = BleConnectionState(ConnectionStatus.CONNECTED, device)
                    }
                }
                override fun gpsFrame(bytes: ByteArray) {
                    val copy = bytes.copyOf()
                    event { if (phase == Phase.STREAMING) source.acceptGps(copy) }
                }
                override fun gpsUnavailable() = event { source.gpsUnavailable() }
                override fun frame(bytes: ByteArray) {
                    val copy = bytes.copyOf()
                    event { if (phase == Phase.STREAMING) source.accept(copy) }
                }
                override fun disconnected() = event {
                    release()
                    source.reset(SensorDataStatus.DISCONNECTED)
                    mutableState.value = BleConnectionState(ConnectionStatus.DISCONNECTED, device)
                }
                override fun failed(message: String) = event { fail(message) }
            })
        } catch (_: SecurityException) { fail("Falta el permiso de conexión Bluetooth.") }
        catch (_: IllegalArgumentException) { fail("La dirección del dispositivo BLE no es válida.") }
        catch (_: IllegalStateException) { fail("No se pudo abrir GATT. Comprueba Bluetooth y vuelve a conectar.") }
    } }

    private fun fail(message: String) {
        release()
        source.reset(SensorDataStatus.FAILED)
        mutableState.value = state.value.copy(status = ConnectionStatus.FAILED, error = message)
    }

    private fun release() {
        generation++
        phase = Phase.IDLE
        timeout?.cancel()
        timeout = null
        session?.close()
        session = null
    }

    companion object { const val CONNECT_TIMEOUT_MILLIS = 20_000L }
}
