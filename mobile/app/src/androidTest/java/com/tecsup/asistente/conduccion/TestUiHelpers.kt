package com.tecsup.asistente.conduccion

import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.tecsup.asistente.conduccion.core.ui.AsistenteVialTheme
import com.tecsup.asistente.conduccion.feature.login.AccessViewModel
import com.tecsup.asistente.conduccion.navigation.AppNavHost
import com.tecsup.asistente.conduccion.navigation.AppRoute
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

fun ComposeContentTestRule.waitForText(text: String) {
    waitUntil(15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}

fun ComposeContentTestRule.clickText(text: String) {
    waitUntil(15_000) { onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithText(text).performClick()
}


/** Enters the guest fixture directly for existing trip and persistence tests. */
fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.openAnonymousTripFixture() {
    activityRule.scenario.onActivity { activity ->
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
}
