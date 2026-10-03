package com.tecsup.asistente.conduccion.domain.ble

data class BleDevice(val address: String, val name: String?)

enum class BleAvailability {
    CHECKING, UNSUPPORTED, PERMISSION_REQUIRED, BLUETOOTH_OFF, LOCATION_OFF, READY, UNAVAILABLE,
}

enum class ScanStatus { IDLE, SCANNING, STOPPED, TIMED_OUT, BLOCKED, FAILED }
enum class ScanFailureReason { PERMISSION, BLUETOOTH_UNAVAILABLE, PLATFORM, UNKNOWN }
data class ScanFailure(val reason: ScanFailureReason, val platformCode: Int? = null)

data class BleScanState(
    val availability: BleAvailability = BleAvailability.CHECKING,
    val status: ScanStatus = ScanStatus.IDLE,
    val devices: List<BleDevice> = emptyList(),
    val failure: ScanFailure? = null,
)

enum class ConnectionStatus {
    NOT_SELECTED, SELECTED,
    CONNECTING, CONNECTED, DISCONNECTED, FAILED,
}

data class BleConnectionState(
    val status: ConnectionStatus = ConnectionStatus.NOT_SELECTED,
    val device: BleDevice? = null,
    val error: String? = null,
)
