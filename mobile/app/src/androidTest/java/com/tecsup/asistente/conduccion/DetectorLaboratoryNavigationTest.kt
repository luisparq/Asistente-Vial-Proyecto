package com.tecsup.asistente.conduccion

import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tecsup.asistente.conduccion.core.ui.AsistenteVialTheme
import com.tecsup.asistente.conduccion.feature.login.AccessViewModel
import com.tecsup.asistente.conduccion.navigation.AppNavHost
import com.tecsup.asistente.conduccion.navigation.AppRoute
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DetectorLaboratoryNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun bluetoothOpensLaboratoryAndSampleRelaySeparately() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                AsistenteVialTheme {
                    val controller = rememberNavController()
                    val access: AccessViewModel = hiltViewModel()
                    AppNavHost(controller)
                    LaunchedEffect(controller, access) {
                        controller.currentBackStackEntryFlow.first { it.destination.route == AppRoute.Login.path }
                        access.demo()
                    }
                }
            }
        }
        val bluetooth = compose.activity.getString(R.string.ble_title)
        val laboratory = compose.activity.getString(R.string.detector_lab_title)
        val relay = compose.activity.getString(R.string.diagnostic_title)

        compose.waitForText(bluetooth)
        compose.onNodeWithText(bluetooth).performScrollTo().performClick()
        compose.onNodeWithTag("ble_list").performScrollToNode(hasText(laboratory))
        compose.onNodeWithText(laboratory).performClick()
        compose.onNodeWithText("Laboratorio del detector · TEST_ONLY").assertExists()

        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.onNodeWithTag("ble_list").performScrollToNode(hasText(relay))
        compose.onNodeWithText(relay).performClick()
        compose.onNodeWithText("Envío de muestras").assertIsDisplayed()
    }
}
