package com.tecsup.asistente.conduccion

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.core.ui.AsistenteVialTheme
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.feature.diagnostic.*
import org.junit.Rule
import org.junit.Test

/** Explicit TEST fixtures: validates UI only, not an ESP32 radio connection. */
class DiagnosticRawUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun showsAllSixRawCountsAndRemovesThemWhenNoLongerReceiving() {
        val state = mutableStateOf(DiagnosticUiState(
            BleConnectionState(ConnectionStatus.CONNECTED, BleDevice("TEST", "TEST IMU")),
            SensorDataStatus.RECEIVING, RawImuSample(12,-34,56,-78,90,-123)))
        compose.setContent { AsistenteVialTheme { DiagnosticContent(state.value, {}) } }
        listOf("ax: 12", "ay: -34", "az: 56", "gx: -78", "gy: 90", "gz: -123").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        for (status in listOf(SensorDataStatus.WAITING_FOR_DATA, SensorDataStatus.FAILED, SensorDataStatus.DISCONNECTED)) {
            // Even if a combined Flow briefly carries the previous sample, never display it as current.
            compose.runOnIdle { state.value = state.value.copy(sensorStatus = status) }
            compose.onNodeWithText("ax: 12").assertDoesNotExist()
            val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.diagnostic_no_measurements)
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }
}
