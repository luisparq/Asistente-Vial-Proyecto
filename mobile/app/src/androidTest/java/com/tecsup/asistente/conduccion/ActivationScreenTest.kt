package com.tecsup.asistente.conduccion

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tecsup.asistente.conduccion.domain.access.AccountActivation
import com.tecsup.asistente.conduccion.domain.sync.MemorySessionStore
import com.tecsup.asistente.conduccion.feature.login.ActivationScreen
import com.tecsup.asistente.conduccion.feature.login.ActivationViewModel
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated UI and fake activation; no real account, token or Room writes. */
@RunWith(AndroidJUnit4::class)
class ActivationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingRequestDisablesSubmitThenSuccessAllowsNormalLogin() {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var loginUrl: String? = null
        val model = ActivationViewModel(AccountActivation { _, _, _, _ -> calls++; gate.await() }, MemorySessionStore())
        compose.setContent { MaterialTheme { ActivationScreen({}, { loginUrl = it }, model, configuredBaseUrl = "http://localhost/") } }
        compose.onNodeWithText("URL del servidor").assertDoesNotExist()
        compose.onNodeWithText("Código de activación").performScrollTo().performTextInput("a".repeat(64))
        compose.onNodeWithText("Nueva contraseña").performScrollTo().performTextInput("TEST password 123")
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().performTextInput("TEST password 123")
        compose.onNode(hasText("Activar cuenta") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Activando…").performScrollTo().assertIsNotEnabled()
        assertEquals(1, calls)
        compose.runOnIdle { gate.complete(Unit) }
        compose.waitUntil(5000) { model.state.value.activated }
        compose.onNodeWithText("Continuar al inicio de sesión").performScrollTo().performClick()
        assertEquals("http://localhost/", loginUrl)
        compose.onNodeWithText("Cuenta activada.", substring = true).performScrollTo().assertIsDisplayed()
    }
}
