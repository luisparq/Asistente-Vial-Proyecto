package com.tecsup.asistente.conduccion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    fun opensActivationFromLoginAndReturnsWithoutRequiringWeb() {
        compose.waitForText("Activar mi cuenta")
        compose.onNodeWithText("Activar mi cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Código de activación").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nueva contraseña").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirmar contraseña").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.back)).performClick()
        compose.onNodeWithText("Activar mi cuenta").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun exploresEveryScreenAndReturnsToAccess() {
        fun text(id: Int) = compose.activity.getString(id)
        compose.clickText(text(R.string.explore))
        listOf(R.string.profile_title, R.string.ble_title,
            R.string.history_title, R.string.settings_title).forEach { title ->
            compose.waitForText(text(title))
            compose.onNodeWithText(text(title)).performScrollTo().performClick()
            compose.onNodeWithText(text(title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.back)).performClick()
            compose.onNodeWithText(text(R.string.home_title)).assertIsDisplayed()
        }
        compose.onNodeWithText(text(R.string.ble_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.diagnostic_title)).performClick()
        compose.onNodeWithText(text(R.string.diagnostic_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.back)).performClick()
        compose.onNodeWithText(text(R.string.settings_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.return_access)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.explore)).performScrollTo().assertIsDisplayed()
        // Reentrar confirma que el retorno al acceso permite iniciar otra exploración.
        compose.onNodeWithText(text(R.string.explore)).performClick()
        compose.onNodeWithText(text(R.string.home_title)).assertIsDisplayed()
    }
}
