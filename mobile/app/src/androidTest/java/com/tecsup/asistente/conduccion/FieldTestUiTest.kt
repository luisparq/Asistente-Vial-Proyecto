package com.tecsup.asistente.conduccion

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tecsup.asistente.conduccion.data.diagnostics.SensorRelayState
import com.tecsup.asistente.conduccion.feature.diagnostic.SensorRelayContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The conductor screen relays device packets; it has no calibration controls. */
class FieldTestUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun senderOffersOneActionAndNoCalibrationOrManualLabels() {
        var started = false
        compose.setContent { MaterialTheme {
            SensorRelayContent(SensorRelayState(), { started = true }, {})
        } }
        compose.onNode(hasText("Enviar muestras a administrador") and hasClickAction()).performScrollTo().performClick()
        assertTrue(started)
        compose.onNodeWithText("Calibrar reposo").assertDoesNotExist()
        compose.onNodeWithText("Aprender: aceleración normal").assertDoesNotExist()
        compose.onNodeWithText("Frenada").assertDoesNotExist()
        compose.onNodeWithText("0 muestras pendientes · 0 confirmadas en esta sesión").assertExists()
    }

    @Test fun activeSenderShowsPendingAcknowledgedAndBufferLossThenAllowsStopping() {
        var stopped = false
        val state = SensorRelayState(sending = true, pending = 42, acknowledged = 100, dropped = 2,
            message = "Sin Internet: pendientes conservados")
        compose.setContent { MaterialTheme {
            SensorRelayContent(state, { stopped = true }, {})
        } }
        compose.onNodeWithText("42 muestras pendientes · 100 confirmadas en esta sesión").assertExists()
        compose.onNodeWithText("Sin Internet: pendientes conservados").assertExists()
        compose.onNodeWithText("2 tramas omitidas por saturación del búfer de recepción").assertExists()
        compose.onNodeWithText("Detener envío de muestras").performScrollTo().performClick()
        assertTrue(stopped)
        compose.onNodeWithText("Iniciar prueba con buzzer").assertDoesNotExist()
    }
}
