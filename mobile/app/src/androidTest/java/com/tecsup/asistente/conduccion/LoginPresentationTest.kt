package com.tecsup.asistente.conduccion

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tecsup.asistente.conduccion.core.ui.LocalDemoMode
import com.tecsup.asistente.conduccion.core.ui.ScreenLayout
import com.tecsup.asistente.conduccion.feature.login.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Presentation tests use explicit TEST input, not real credentials or backend writes. */
class LoginPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun conventionalLoginSendsFieldsAndSupportsPasswordVisibility() {
        var submitted: Pair<String, String>? = null
        compose.setContent { MaterialTheme {
            LoginScreen({}, {}, { email, password -> submitted = email to password }, {})
        } }
        compose.onNodeWithText("URL base del servidor").assertDoesNotExist()
        compose.onNodeWithText("Iniciar sesión").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Correo").performTextInput("TEST@example.invalid")
        compose.onNodeWithText("Contraseña").performTextInput("TEST password 123")
        compose.onNodeWithText("Mostrar").performClick()
        compose.onNodeWithText("Ocultar").assertIsDisplayed().performClick()
        compose.onNodeWithText("Iniciar sesión").performClick()
        assertEquals("TEST@example.invalid" to "TEST password 123", submitted)
    }

    @Test fun demoAndActivationAreSeparateSecondaryActions() {
        var demo = 0
        var activation = 0
        compose.setContent { MaterialTheme { LoginScreen({ demo++ }, { activation++ }, { _, _ -> }, {}) } }
        compose.onNodeWithText("Activar mi cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Probar modo demo").performScrollTo().performClick()
        assertEquals(1, activation)
        assertEquals(1, demo)
    }

    @Test fun busyLoginPreventsExtraSubmissions() {
        compose.setContent { MaterialTheme {
            LoginScreen({}, {}, { _, _ -> }, {}, AccessState(busy = true))
        } }
        compose.onNodeWithText("Iniciando sesión…").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Activar mi cuenta").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Probar modo demo").performScrollTo().assertIsNotEnabled()
    }

    @Test fun welcomeUsesProvidedNameWithoutInventingProfile() {
        compose.setContent { MaterialTheme { WelcomeScreen("TEST conductor", {}) } }
        compose.onNodeWithText("Hola, TEST conductor").assertIsDisplayed()
        compose.onNodeWithText("Bienvenido a Asistente Vial").assertIsDisplayed()
    }

    @Test fun demoShowsExplicitSimulatedNotice() {
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalDemoMode provides true) {
            ScreenLayout("Inicio", "") {}
        } } }
        compose.onNodeWithText("Modo demostración").assertIsDisplayed()
        compose.onNodeWithText("Los datos y eventos generados en este modo son simulados y no representan detecciones físicas reales.").assertIsDisplayed()
    }
}
