package com.tecsup.asistente.conduccion.data.ble

/** Frontera Android para probar el ciclo GATT sin radio ni muestras físicas inventadas. */
interface GattTransport {
    fun connect(address: String, listener: Listener): Session
    interface Session {
        fun discover(): Boolean
        fun subscribe(): Boolean
        fun close()
    }
    interface Listener {
        fun connected()
        fun services(profile: GattProfileDescription)
        fun subscribed()
        fun gpsFrame(bytes: ByteArray) {}
        fun gpsUnavailable() {}
        fun frame(bytes: ByteArray)
        fun disconnected()
        fun failed(message: String)
    }
}
