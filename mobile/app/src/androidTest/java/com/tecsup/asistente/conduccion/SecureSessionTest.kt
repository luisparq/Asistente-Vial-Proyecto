package com.tecsup.asistente.conduccion

import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.data.network.SecureSessionStore
import com.tecsup.asistente.conduccion.domain.sync.Session
import org.junit.Assert.*
import org.junit.Test

class SecureSessionTest {
    @Test fun rememberedBleDeviceIsEncryptedScopedAndSurvivesSessionLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = SecureSessionStore(context)
        val previous = session.state.value
        val owner = "https://ble-test.invalid/#987654/987654"
        val other = "$owner-other"
        val prefs = com.tecsup.asistente.conduccion.data.ble.SecureBleDeviceStore(context, session)
        val original = prefs.read(owner)
        val fixture = com.tecsup.asistente.conduccion.domain.ble.RememberedBleDevice("AA:BB:CC:DD:EE:01", "TEST-only BLE", false)
        try {
            prefs.save(owner, fixture)
            val raw = context.getSharedPreferences("encrypted_ble_devices", 0).all.values.joinToString()
            assertFalse(raw.contains(fixture.address)); assertFalse(raw.contains(fixture.name!!))
            assertEquals(fixture, com.tecsup.asistente.conduccion.data.ble.SecureBleDeviceStore(context, SecureSessionStore(context)).read(owner))
            assertNull(prefs.read(other))
            session.save(null)
            assertEquals(fixture, prefs.read(owner))
            prefs.save(owner, null); assertNull(prefs.read(owner))
        } finally { prefs.save(owner, original); session.save(previous) }
    }
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
