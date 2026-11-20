package com.tecsup.asistente.conduccion

import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun mapLayersAllowSelectionAndExplainWhenLoginIsRequired() {
        compose.clickText(compose.activity.getString(R.string.explore))
        compose.waitForText("Mi mapa")
        compose.clickText("Mapa")
        compose.clickText("Capas")
        compose.onNodeWithText("Capas del mapa").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tráfico en vivo").performScrollTo().performClick().assertIsOn()
        compose.onAllNodesWithText("Inicia sesión para consultar TomTom").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Colegios").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Talleres").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Límites de velocidad · OSM").performScrollTo().assertIsOn()
    }

    @Test
    fun opensOptionalDriverMapAndReturnsWithoutGpsOrAnActiveTrip() {
        compose.clickText(compose.activity.getString(R.string.explore))
        compose.waitForText("Mi mapa")
        compose.onNodeWithText("Mapa").performClick()
        compose.onNodeWithText("AVIGEM One desconectado").assertIsDisplayed()
        compose.onNodeWithText("Detalles y alertas").performClick()
        compose.onNodeWithText("Dispositivo BLE desconectado").assertIsDisplayed()
        compose.onNodeWithText("Inicio").performClick()
        compose.onAllNodesWithText(compose.activity.getString(R.string.home_title)).onFirst().assertIsDisplayed()
    }

    @Test
    fun opensActivationFromLoginAndReturnsWithoutRequiringWeb() {
        compose.clickText("Iniciar sesión")
        compose.waitForText("Activar cuenta")
        compose.onNodeWithText("Activar cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Código de activación").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nueva contraseña").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.clickText("Iniciar sesión")
        compose.onNodeWithText("Activar cuenta").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun exploresEveryScreenAndReturnsToAccess() {
        fun text(id: Int) = compose.activity.getString(id)
        compose.clickText(text(R.string.explore))
        listOf("Perfil" to R.string.profile_title, "Historial" to R.string.history_title).forEach { (tab, title) ->
            compose.clickText(tab)
            compose.onAllNodesWithText(text(title)).onFirst().assertIsDisplayed()
            compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        }
        compose.onNodeWithText(text(R.string.ble_title)).performScrollTo().performClick()
        compose.onNodeWithTag("ble_list").performScrollToNode(hasText(text(R.string.diagnostic_title)))
        compose.onNodeWithText(text(R.string.diagnostic_title)).performClick()
        compose.onNodeWithText(text(R.string.diagnostic_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()
        compose.onNodeWithContentDescription(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.return_access)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.explore)).performScrollTo().assertIsDisplayed()
        // Reentrar confirma que el retorno al acceso permite iniciar otra exploración.
        compose.onNodeWithText(text(R.string.explore)).performClick()
        compose.onAllNodesWithText(text(R.string.home_title)).onFirst().assertIsDisplayed()
    }
}
