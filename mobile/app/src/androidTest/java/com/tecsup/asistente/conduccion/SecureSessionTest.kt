package com.tecsup.asistente.conduccion

import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.network.SecureSessionStore
import com.tecsup.asistente.conduccion.domain.sync.Session
import org.junit.Assert.*
import org.junit.Test

class SecureSessionTest {
    @Test fun tokenIsEncryptedAndRestoredWithoutPlaintextPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureSessionStore(context)
        val previous = store.state.value
        try {
            val fixture = Session("https://test.invalid/", 1, 1, "TEST ONLY", "test@example.invalid", "TEST-only-sensitive-token")
            store.save(fixture)
            val persisted = context.getSharedPreferences("encrypted_api_session", 0).getString("session", "")!!
            assertFalse(persisted.contains(fixture.token!!))
            assertFalse(persisted.contains(fixture.email))
            assertEquals(fixture, SecureSessionStore(context).state.value)
            store.save(null)
            assertNull(SecureSessionStore(context).state.value)
        } finally { store.save(previous) }
    }
}
