package com.tecsup.asistente.conduccion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun exploresEveryScreenAndReturnsToAccess() {
        fun text(id: Int) = compose.activity.getString(id)
        compose.clickText(text(R.string.explore))
        listOf(R.string.ble_title, R.string.diagnostic_title,
            R.string.history_title, R.string.settings_title).forEach { title ->
            compose.clickText(text(title))
            compose.onNodeWithText(text(title)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.back)).performClick()
            compose.onNodeWithText(text(R.string.home_title)).assertIsDisplayed()
        }
        compose.onNodeWithText(text(R.string.settings_title)).performClick()
        compose.onNodeWithText(text(R.string.return_access)).performClick()
        compose.onNodeWithText(text(R.string.explore)).assertIsDisplayed()
        // Reentrar confirma que el retorno al acceso permite iniciar otra exploración.
        compose.onNodeWithText(text(R.string.explore)).performClick()
        compose.onNodeWithText(text(R.string.home_title)).assertIsDisplayed()
    }
}
