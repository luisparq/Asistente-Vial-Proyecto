package com.tecsup.asistente.conduccion.data.ble

/** UUID v4 propios, definidos una vez para este proyecto. Ver docs/ETAPA_3B_BLE.md. */
object TelemetryGattProfile {
    const val SERVICE = "73e8ca02-41d7-45bd-9d39-7187649746ca"
    const val IMU_RAW = "e77a0a03-3641-40f2-87b7-f178622259cd"
    const val CCCD = "00002902-0000-1000-8000-00805f9b34fb"
}

data class GattProfileDescription(val service: Boolean, val characteristic: Boolean, val notify: Boolean, val cccd: Boolean) {
    fun error(): String? = when {
        !service -> "El dispositivo no ofrece el servicio de telemetría esperado."
        !characteristic -> "No se encontró la característica IMU_RAW."
        !notify -> "IMU_RAW no admite notificaciones."
        !cccd -> "No se encontró el descriptor CCCD para suscribirse."
        else -> null
    }
}
