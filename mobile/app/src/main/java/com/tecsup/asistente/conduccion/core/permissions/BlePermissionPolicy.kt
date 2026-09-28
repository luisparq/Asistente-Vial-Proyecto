package com.tecsup.asistente.conduccion.core.permissions

/** Política pura para poder verificar todas las versiones sin múltiples dispositivos. */
object BlePermissionPolicy {
    const val SCAN = "android.permission.BLUETOOTH_SCAN"
    const val CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"

    fun runtimePermissions(sdk: Int): List<String> =
        if (sdk >= 31) listOf(SCAN, CONNECT) else listOf(FINE_LOCATION)

    fun requiresLocationService(sdk: Int): Boolean = sdk < 31
}
