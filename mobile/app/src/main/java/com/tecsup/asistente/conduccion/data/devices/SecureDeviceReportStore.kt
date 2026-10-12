package com.tecsup.asistente.conduccion.data.devices

import android.content.Context
import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.network.SecureSessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class SecureDeviceReportStore @Inject constructor(@ApplicationContext context: Context,
    private val encryption: SecureSessionStore) : DeviceReportStore {
    private val prefs = context.getSharedPreferences("encrypted_device_reports", Context.MODE_PRIVATE)
    @Synchronized override fun read(owner: String): DeviceReportEntry? = try {
        prefs.getString(tokenHash(owner),null)?.let { Contract.json.decodeFromString<DeviceReportEntry>(encryption.decryptLocal(it)) }
    } catch (_: Exception) { null } // Never replace an unreadable encrypted record with fabricated telemetry.
    @Synchronized override fun write(owner: String, value: DeviceReportEntry) {
        check(prefs.edit().putString(tokenHash(owner),encryption.encryptLocal(Contract.json.encodeToString(value))).commit())
    }
}
