package com.tecsup.asistente.conduccion

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
