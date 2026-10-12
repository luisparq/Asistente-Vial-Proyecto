package com.tecsup.asistente.conduccion.data.ble

import com.tecsup.asistente.conduccion.core.di.BleScope
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Bounded foreground orchestration of the existing GATT connector, no scanner/service. */
@Singleton
class BleReconnectController @Inject constructor(private val platform: BlePlatform,
    private val connector: BleConnection, private val accounts: SessionStore,
    private val preferences: RememberedBleStore, @BleScope private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(BleReconnectState())
    val state = mutable.asStateFlow()
    private var owner: String? = null
    private var remembered: RememberedBleDevice? = null
    private var foreground = false
    private var available = platform.availability()
    private var attempts = 0
    private var job: Job? = null
    private var generation = 0L

    init {
        scope.launch { accounts.state.collect { account ->
            if (owner != account?.ownerKey) {
                stopCycle()
                connector.clear() // Never carry another account's GATT into this session.
                owner = account?.ownerKey
                remembered = owner?.let(preferences::read)
                attempts = 0
                mutable.value = BleReconnectState(enabled = remembered?.automatic == true)
                remembered?.let { connector.select(it.device()) }
                startCycle()
            }
        } }
        scope.launch { platform.observeAvailability().catch { emit(BleAvailability.UNAVAILABLE) }.collect { value ->
            val restored = available != BleAvailability.READY && value == BleAvailability.READY
            available = value
            if (value != BleAvailability.READY) stopCycle()
            else {
                if (restored) attempts = 0
                startCycle()
            }
        } }
        scope.launch { connector.state.collect { connection ->
            if (connection.status == ConnectionStatus.CONNECTED) mutable.value = state.value.copy(running = false, message = null)
            if (connection.status in listOf(ConnectionStatus.SELECTED, ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED)) startCycle()
        } }
    }

    fun foreground(value: Boolean) { scope.launch {
        if (foreground == value) return@launch
        foreground = value
        if (value) { attempts = 0; refreshNow() }
        else {
            val connecting = job?.isActive == true && connector.state.value.status == ConnectionStatus.CONNECTING
            stopCycle()
            if (connecting) remembered?.let { connector.select(it.device()) }
        }
    } }
    fun refresh() { scope.launch { refreshNow() } }
    private fun refreshNow() {
        available = platform.availability()
        if (available != BleAvailability.READY) stopCycle() else startCycle()
    }
    private fun persist(value: RememberedBleDevice?) {
        remembered = value
        try { owner?.let { preferences.save(it, value) } }
        catch (_: Exception) {
            remembered = value?.copy(automatic = false)
            mutable.value = state.value.copy(message = "No se pudo guardar la configuración BLE; reconexión suspendida.")
        }
        mutable.value = state.value.copy(enabled = remembered?.automatic == true)
    }
    fun select(device: BleDevice) { scope.launch {
        stopCycle(); attempts = 0
        persist(RememberedBleDevice(device.address, device.name, false))
        connector.select(device)
    } }
    fun connectSelected() { scope.launch {
        val device = remembered?.device() ?: connector.state.value.device ?: return@launch
        if (connector.state.value.device == device && connector.state.value.status in listOf(ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTED)) return@launch
        if (owner == null) { connector.prepare(); return@launch } // Demo remains manual, unassigned.
        enableNow(device)
    } }
    fun enable() { scope.launch {
        if (owner == null) {
            mutable.value = state.value.copy(message = "Inicia sesión para recordar el dispositivo de tu cuenta.")
            return@launch
        }
        val device = remembered?.device() ?: connector.state.value.device ?: return@launch
        if (job?.isActive == true) return@launch
        enableNow(device)
    } }
    private fun enableNow(device: BleDevice) {
        stopCycle(); attempts = 0
        persist(RememberedBleDevice(device.address, device.name, true))
        startCycle()
    }
    fun disconnect() { scope.launch {
        stopCycle()
        val device = connector.state.value.device ?: remembered?.device()
        persist(device?.let { RememberedBleDevice(it.address, it.name, false) })
        if (device != null) connector.select(device) else connector.clear()
        mutable.value = state.value.copy(message = "Desconectado. Reconexión automática suspendida.")
    } }
    fun forget() { scope.launch {
        stopCycle(); attempts = 0; persist(null); connector.clear()
        mutable.value = BleReconnectState()
    } }
    private fun stopCycle() {
        generation++
        job?.cancel(); job = null
        mutable.value = state.value.copy(running = false)
    }
    private fun allowed() = foreground && available == BleAvailability.READY && owner != null &&
        owner == accounts.state.value?.ownerKey && remembered?.automatic == true
    private fun startCycle() {
        if (!allowed() || job?.isActive == true || state.value.running || connector.state.value.status in
            listOf(ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING)) return
        if (attempts >= RETRY_DELAYS.size) {
            mutable.value = state.value.copy(running = false, message = "Reintentos agotados. Pulsa Habilitar reconexión para volver a intentar.")
            return
        }
        val token = generation
        job = scope.launch {
            try {
                while (allowed() && attempts < RETRY_DELAYS.size) {
                    val pause = RETRY_DELAYS[attempts]
                    mutable.value = state.value.copy(running = true, attempt = attempts + 1,
                        message = "Reconectando: intento ${attempts + 1}/4" + if (pause > 0) ". Espera ${pause / 1000} s." else ".")
                    delay(pause)
                    if (!allowed() || token != generation) break
                    val device = remembered!!.device()
                    attempts++
                    connector.select(device)
                    connector.state.first { it.status == ConnectionStatus.SELECTED && it.device == device }
                    connector.prepare()
                    val end = connector.state.first { it.device == device && it.status in
                        listOf(ConnectionStatus.CONNECTED, ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED) }
                    if (end.status == ConnectionStatus.CONNECTED) break
                }
            } finally {
                if (token == generation) {
                    job = null
                    mutable.value = state.value.copy(running = false,
                        message = if (attempts >= RETRY_DELAYS.size && connector.state.value.status != ConnectionStatus.CONNECTED)
                            "Reintentos agotados. Pulsa Habilitar reconexión para volver a intentar." else state.value.message)
                }
            }
        }
    }
    companion object { val RETRY_DELAYS = listOf(0L, 5_000L, 15_000L, 30_000L) }
}
