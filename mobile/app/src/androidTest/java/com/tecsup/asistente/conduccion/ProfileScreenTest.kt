package com.tecsup.asistente.conduccion

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tecsup.asistente.conduccion.domain.profile.DriverProfile
import com.tecsup.asistente.conduccion.domain.profile.ProfileState
import com.tecsup.asistente.conduccion.feature.profile.ProfileContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** In-memory UI fixtures only; no session or manual Room data is modified. */
@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyProfileDisplaysExplicitEmptyStates() {
        compose.setContent { MaterialTheme { ProfileContent(ProfileState.Ready(DriverProfile("TEST Driver", "test@example.test", "ACTIVE", "ACTIVE", null, emptyList(), null)), {}, {}, {}) } }
        compose.onNodeWithText("TEST Driver").assertIsDisplayed()
        listOf("Sin contacto de emergencia registrado.", "Sin vehículo asignado.", "Sin documentos registrados.").forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }
    @Test fun offlineAndExpiredSessionHaveActionableMessages() {
        val state = mutableStateOf<ProfileState>(ProfileState.Offline)
        compose.setContent { MaterialTheme { ProfileContent(state.value, {}, {}, {}) } }
        compose.onNodeWithText("No se pudo conectar con el servidor.", substring = true).assertIsDisplayed()
        compose.runOnIdle { state.value = ProfileState.SessionExpired }
        compose.onNodeWithText("La sesión no es válida o ha expirado.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ajustes").performScrollTo().assertIsDisplayed()
    }
}
