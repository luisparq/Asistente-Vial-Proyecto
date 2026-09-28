package com.tecsup.asistente.conduccion

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.Lifecycle
import com.tecsup.asistente.conduccion.core.ui.AsistenteVialTheme
import com.tecsup.asistente.conduccion.domain.ble.*
import com.tecsup.asistente.conduccion.feature.ble.BleScreen
import com.tecsup.asistente.conduccion.feature.ble.BleViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Interfaz con dobles de BLE. Estas pruebas no validan radio ni conexión física. */
class BleUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun text(id: Int) = compose.activity.getString(id)

    private fun show(repository: RecordingBleRepository) {
        compose.setContent {
            AsistenteVialTheme { BleScreen(onBack = {}, viewModel = repository.viewModel) }
        }
        compose.activityRule.scenario.onActivity { it.viewModelStore.put("ble-test", repository.viewModel) }
    }

    @Test fun fixtureSelectionRemainsPendingWithoutConnecting() {
        val repository = RecordingBleRepository()
        show(repository)
        compose.clickText(text(R.string.ble_start))
        compose.onNodeWithTag("ble_list").performScrollToNode(hasText(text(R.string.ble_select)))
        compose.clickText(text(R.string.ble_select))
        compose.onNodeWithTag("ble_list").performScrollToNode(hasText(text(R.string.ble_prepare)))
        compose.clickText(text(R.string.ble_prepare))
        compose.waitForText(text(R.string.connection_pending))
        assertEquals(ConnectionStatus.AWAITING_GATT_PROFILE, repository.connection.value.status)
        assertEquals(ScanStatus.STOPPED, repository.scan.value.status)
        compose.onNodeWithText(text(R.string.connection_connected)).assertDoesNotExist()
    }

    @Test fun backgroundStopsTestScannerAndReturningDoesNotRestartIt() {
        val repository = RecordingBleRepository()
        show(repository)
        compose.clickText(text(R.string.ble_start))
        compose.waitForText(text(R.string.ble_stop))
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals(ScanStatus.STOPPED, repository.scan.value.status)
        assertTrue(repository.stopCalls > 0)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForText(text(R.string.ble_start))
        assertEquals(1, repository.startCalls)
    }

    @Test fun missingPermissionOffersExplicitRequestAndSettings() {
        val repository = RecordingBleRepository(BleAvailability.PERMISSION_REQUIRED)
        show(repository)
        compose.waitForText(text(R.string.ble_grant_permissions))
        compose.onNodeWithText(text(R.string.ble_app_settings)).assertIsEnabled()
        compose.onNodeWithTag("ble_list").performScrollToNode(hasText(text(R.string.ble_start)))
        compose.onNodeWithText(text(R.string.ble_start)).assertIsNotEnabled()
        assertEquals(0, repository.startCalls)
    }

    @Test fun unsupportedDeviceDisablesScanButKeepsScreenUsable() {
        val repository = RecordingBleRepository(BleAvailability.UNSUPPORTED)
        show(repository)
        compose.waitForText(text(R.string.ble_unsupported))
        compose.onNodeWithText(text(R.string.ble_start)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.back)).assertIsEnabled()
    }
}

private class RecordingBleRepository(availability: BleAvailability = BleAvailability.READY) : BleRepository {
    private val mutableScan = MutableStateFlow(BleScanState(availability = availability))
    private val mutableConnection = MutableStateFlow(BleConnectionState())
    override val scan = mutableScan.asStateFlow()
    override val connection = mutableConnection.asStateFlow()
    val viewModel by lazy { BleViewModel(this) }
    var startCalls = 0
    var stopCalls = 0
    override fun refresh() = Unit
    override fun startScan() {
        startCalls++
        mutableScan.value = scan.value.copy(status = ScanStatus.SCANNING,
            devices = listOf(BleDevice("ui-test-fixture", "Fixture de interfaz; no hardware")))
    }
    override fun stopScan() {
        stopCalls++
        if (scan.value.status == ScanStatus.SCANNING) mutableScan.value = scan.value.copy(status = ScanStatus.STOPPED)
    }
    override fun selectDevice(address: String) {
        stopScan()
        mutableConnection.value = BleConnectionState(ConnectionStatus.SELECTED,
            scan.value.devices.first { it.address == address })
    }
    override fun prepareConnection() {
        mutableConnection.value = connection.value.copy(status = ConnectionStatus.AWAITING_GATT_PROFILE)
    }
    override fun clearSelection() { mutableConnection.value = BleConnectionState() }
}
