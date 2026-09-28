package com.tecsup.asistente.conduccion.core.ui

import com.tecsup.asistente.conduccion.R
import com.tecsup.asistente.conduccion.domain.ble.ConnectionStatus

fun ConnectionStatus.labelResource(): Int = when (this) {
    ConnectionStatus.NOT_SELECTED -> R.string.connection_none
    ConnectionStatus.SELECTED -> R.string.connection_selected
    ConnectionStatus.AWAITING_GATT_PROFILE -> R.string.connection_pending
    ConnectionStatus.CONNECTING -> R.string.connection_connecting
    ConnectionStatus.CONNECTED -> R.string.connection_connected
    ConnectionStatus.DISCONNECTED -> R.string.connection_disconnected
    ConnectionStatus.FAILED -> R.string.connection_failed
}
