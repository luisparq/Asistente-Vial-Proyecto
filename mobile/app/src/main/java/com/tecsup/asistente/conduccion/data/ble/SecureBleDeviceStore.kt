package com.tecsup.asistente.conduccion.data.ble

import android.content.Context
import com.tecsup.asistente.conduccion.data.network.Contract
import com.tecsup.asistente.conduccion.data.network.SecureSessionStore
import com.tecsup.asistente.conduccion.domain.ble.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString

@Singleton
class SecureBleDeviceStore @Inject constructor(@ApplicationContext context: Context,
    private val encryption: SecureSessionStore) : RememberedBleStore {
    private val prefs = context.getSharedPreferences("encrypted_ble_devices", Context.MODE_PRIVATE)
    private fun key(owner: String) = MessageDigest.getInstance("SHA-256").digest(owner.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    @Synchronized override fun read(owner: String): RememberedBleDevice? = try {
        prefs.getString(key(owner), null)?.let {
            Contract.json.decodeFromString<RememberedBleDevice>(encryption.decryptLocal(it))
        }
    } catch (_: Exception) { prefs.edit().remove(key(owner)).commit(); null }
    @Synchronized override fun save(owner: String, device: RememberedBleDevice?) {
        val edit = prefs.edit()
        if (device == null) edit.remove(key(owner))
        else edit.putString(key(owner), encryption.encryptLocal(Contract.json.encodeToString(device)))
        check(edit.commit()) { "No se pudo guardar el dispositivo BLE." }
    }
}
