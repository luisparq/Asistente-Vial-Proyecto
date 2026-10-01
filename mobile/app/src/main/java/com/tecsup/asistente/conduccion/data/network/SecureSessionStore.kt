package com.tecsup.asistente.conduccion.data.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tecsup.asistente.conduccion.domain.sync.Session
import com.tecsup.asistente.conduccion.domain.sync.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.encodeToString

@Singleton
class SecureSessionStore @Inject constructor(@ApplicationContext context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("encrypted_api_session", Context.MODE_PRIVATE)
    override val gate = Mutex()
    override val state = MutableStateFlow(read())
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("asistente_api_session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("asistente_api_session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun read(): Session? = try {
        prefs.getString("session", null)?.let {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            Contract.json.decodeFromString<Session>(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        }
    } catch (_: Exception) { prefs.edit().clear().commit(); null }

    @Synchronized override fun save(session: Session?) {
        val edit = prefs.edit().clear()
        if (session != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.iv + cipher.doFinal(Contract.json.encodeToString(session).toByteArray(Charsets.UTF_8))
            edit.putString("session", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        }
        check(edit.commit()) { "No se pudo guardar la sesión de forma segura." }
        state.value = session
    }
}
