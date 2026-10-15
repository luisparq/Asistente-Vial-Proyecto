package com.tecsup.asistente.conduccion

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.core.ui.AsistenteVialTheme
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.domain.diagnostic.*
import com.tecsup.asistente.conduccion.feature.diagnostic.*
import com.tecsup.asistente.conduccion.domain.processing.ImuPipeline
import org.junit.Rule
import org.junit.Test

/** Explicit TEST fixtures: validates UI only, not an ESP32 radio connection. */
class DiagnosticRawUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun chartRendersTestSamplesAndDoesNotPresentHistoryAsCurrentAfterDisconnect() {
        val state = mutableStateOf(DiagnosticUiState(connection = BleConnectionState(ConnectionStatus.CONNECTED),
            sensorStatus = SensorDataStatus.RECEIVING))
        val lab = LaboratoryUiState(history = (0..4).map {
            com.tecsup.asistente.conduccion.domain.processing.PhysicalImuSample(
                com.tecsup.asistente.conduccion.domain.processing.Vector3(0.0, 0.0, 1.0),
                com.tecsup.asistente.conduccion.domain.processing.Vector3(0.0, 0.0, 0.0), it * 250L)
        })
        compose.setContent { AsistenteVialTheme { DiagnosticContent(state.value, {}, laboratory = lab) } }
        compose.onNodeWithContentDescription("Gráfica aceleración, X Y Z, últimos 30 segundos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Gráfica giroscopio, X Y Z, últimos 30 segundos").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(connection = BleConnectionState(ConnectionStatus.DISCONNECTED)) }
        compose.onNodeWithContentDescription("Gráfica aceleración, X Y Z, últimos 30 segundos").assertDoesNotExist()
        compose.onNodeWithText("Sin muestras actuales. Conecta BLE y espera recepción IMU.").performScrollTo().assertIsDisplayed()
    }
    @Test fun experimentalLabIsExplicitAndSyntheticActionWorksWithoutBle() {
        var requested = false
        compose.setContent {
            AsistenteVialTheme {
                DiagnosticContent(DiagnosticUiState(), {}, onSynthetic = { requested = it == com.tecsup.asistente.conduccion.domain.model.DrivingEventType.HARSH_BRAKING })
            }
        }
        compose.onNodeWithText("Laboratorio del detector · TEST_ONLY").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ensayar frenada").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(requested) }
        compose.onNodeWithText("Capturar reposo y observar BLE").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Iniciar medición").performScrollTo().assertIsNotEnabled()
    }
    @Test fun gpsSectionRemainsVisibleWithoutFixAndHidesCoordinatesAfterDisconnect() {
        // TEST state only; not a physical GPS position.
        val state = mutableStateOf(DiagnosticUiState(
            connection = BleConnectionState(ConnectionStatus.CONNECTED),
            gps = GpsTelemetry(GpsStatus.NO_FIX)))
        compose.setContent { AsistenteVialTheme { DiagnosticContent(state.value, {}) } }
        compose.onNodeWithText("GPS NEO-6M").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GPS sin fix válido").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Latitud:", substring = true).assertDoesNotExist()
        for ((status, label) in listOf(GpsStatus.NO_RECEPTION to "GPS sin recepción UART",
            GpsStatus.WAITING to "Esperando telemetría GPS", GpsStatus.STALE to "Posición GPS caducada")) {
            compose.runOnIdle { state.value = state.value.copy(gps = GpsTelemetry(status)) }
            compose.onNodeWithText("GPS NEO-6M").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { state.value = state.value.copy(gps = GpsTelemetry(GpsStatus.VALID,
            latitude = -16.0, longitude = -71.0, utcSeconds = 43200, ageMillis = 100)) }
        compose.onNodeWithText("Latitud: -16.0 · Longitud: -71.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Hora UTC de la posición: 12:00").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(connection = BleConnectionState(ConnectionStatus.DISCONNECTED),
            gps = GpsTelemetry()) }
        compose.onNodeWithText("GPS NEO-6M").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GPS desconectado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Latitud:", substring = true).assertDoesNotExist()
    }
    @Test fun showsAllSixRawCountsAndRemovesThemWhenNoLongerReceiving() {
        val raw = RawImuSample(12,-34,56,-78,90,-123, 1234)
        val state = mutableStateOf(DiagnosticUiState(
            BleConnectionState(ConnectionStatus.CONNECTED, BleDevice("TEST", "TEST IMU")),
            SensorDataStatus.RECEIVING, raw, ImuPipeline().accept(raw)))
        compose.setContent { AsistenteVialTheme { DiagnosticContent(state.value, {}) } }
        listOf("ax: 12", "ay: -34", "az: 56", "gx: -78", "gy: 90", "gz: -123").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("PROCESADO").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin calibración física · sin corrección de offsets.").performScrollTo().assertIsDisplayed()
        for (status in listOf(SensorDataStatus.WAITING_FOR_DATA, SensorDataStatus.FAILED, SensorDataStatus.DISCONNECTED)) {
            // Even if a combined Flow briefly carries the previous sample, never display it as current.
            compose.runOnIdle { state.value = state.value.copy(sensorStatus = status) }
            compose.onNodeWithText("ax: 12").assertDoesNotExist()
            compose.onNodeWithText("PROCESADO").assertDoesNotExist()
            val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.diagnostic_no_measurements)
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        compose.runOnIdle { state.value = state.value.copy(sensorStatus = SensorDataStatus.RECEIVING,
            connection = BleConnectionState(ConnectionStatus.DISCONNECTED)) }
        compose.onNodeWithText("ax: 12").assertDoesNotExist()
        compose.onNodeWithText("PROCESADO").assertDoesNotExist()
    }
}
