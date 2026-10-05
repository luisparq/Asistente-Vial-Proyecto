package com.tecsup.asistente.conduccion

import androidx.lifecycle.ViewModelStore
import com.tecsup.asistente.conduccion.data.network.LaravelAccountActivation
import com.tecsup.asistente.conduccion.data.network.LaravelApi
import com.tecsup.asistente.conduccion.domain.access.AccountActivation
import com.tecsup.asistente.conduccion.domain.access.ActivationFailure
import com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore
import com.tecsup.asistente.conduccion.feature.login.ActivationViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** TEST ONLY credentials and mocked HTTP; never creates a local Laravel account. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountActivationTest {
    @Test fun sendsPublicActivationContractWithoutBearerAndAcceptsNoContent() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            LaravelAccountActivation(LaravelApi()).activate(server.url("/").toString(), "a".repeat(64), "TEST password 123", "TEST password 123")
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/auth/activate", request.path)
            assertNull(request.getHeader("Authorization"))
            val fields = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("activation_code", "password", "password_confirmation"), fields.keys)
            assertEquals("a".repeat(64), fields["activation_code"]!!.jsonPrimitive.content)
        }
    }

    @Test fun validationAndRateLimitHaveSafeMessages() = runTest {
        MockWebServer().use { server ->
            val repository = LaravelAccountActivation(LaravelApi())
            for ((status, body, expected) in listOf(
                Triple(422, "{\"error\":{\"fields\":{\"activation_code\":[\"invalid\"]}}}", "Código no válido"),
                Triple(422, "{\"error\":{\"fields\":{\"password\":[\"invalid\"]}}}", "La contraseña"),
                Triple(429, "{}", "Demasiados intentos"),
                Triple(503, "{}", "No se pudo activar"),
            )) {
                server.enqueue(MockResponse().setResponseCode(status).setBody(body))
                try {
                    repository.activate(server.url("/").toString(), "a".repeat(64), "TEST password 123", "TEST password 123")
                    fail("Failure expected")
                } catch (failure: ActivationFailure) {
                    assertTrue(failure.message!!.startsWith(expected))
                    assertFalse(failure.message!!.contains("TEST password"))
                }
            }
        }
    }

    @Test fun repeatedTapsSendOnceThenAllowLoginWithoutChangingSession() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val session = MemorySessionStore()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        try {
            val model = ActivationViewModel(AccountActivation { _, _, _, _ -> calls++; gate.await() }, session)
            store.put("activation", model)
            repeat(10) { model.activate("http://localhost/", "a".repeat(64), "TEST password 123", "TEST password 123") }
            assertEquals(1, calls)
            assertTrue(model.state.value.busy)
            gate.complete(Unit); runCurrent()
            assertTrue(model.state.value.activated)
            assertFalse(model.state.value.busy)
            assertNull(session.state.value)
            model.activate("http://localhost/", "a".repeat(64), "TEST password 123", "TEST password 123")
            assertEquals(1, calls)
        } finally { store.clear(); Dispatchers.resetMain() }
    }

    @Test fun invalidFieldsNeverSendAndFailedAttemptCanBeRetried() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        var calls = 0
        try {
            val model = ActivationViewModel(AccountActivation { _, _, _, _ -> calls++; throw ActivationFailure("TEST network unavailable") }, MemorySessionStore())
            store.put("activation", model)
            model.activate("http://localhost/", "short", "TEST password 123", "TEST password 123")
            model.activate("http://localhost/", "a".repeat(64), "short", "short")
            model.activate("http://localhost/", "a".repeat(64), "TEST password 123", "mismatch")
            assertEquals(0, calls)
            repeat(2) { model.activate("http://localhost/", "a".repeat(64), "TEST password 123", "TEST password 123") }
            assertEquals(2, calls)
            assertFalse(model.state.value.busy)
            assertFalse(model.state.value.activated)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
