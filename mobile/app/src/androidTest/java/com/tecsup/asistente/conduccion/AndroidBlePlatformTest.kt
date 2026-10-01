package com.tecsup.asistente.conduccion

import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.tecsup.asistente.conduccion.core.permissions.BlePermissionPolicy
import com.tecsup.asistente.conduccion.data.ble.AndroidBlePlatform
import com.tecsup.asistente.conduccion.domain.ble.BleAvailability
import com.tecsup.asistente.conduccion.domain.ble.ScanFailure
import org.junit.Assert.*
import org.junit.Test

class AndroidBlePlatformTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun platformSafelyReportsEnvironmentAndBlocksUnavailableScanning() {
        val platform = AndroidBlePlatform(context)
        val availability = platform.availability()
        assertNotEquals(BleAvailability.CHECKING, availability)
        val hasBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val granted = BlePermissionPolicy.runtimePermissions(Build.VERSION.SDK_INT).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasBle) assertEquals(BleAvailability.UNSUPPORTED, availability)
        if (hasBle && !granted) assertEquals(BleAvailability.PERMISSION_REQUIRED, availability)
        if (availability != BleAvailability.READY) {
            var failure: ScanFailure? = null
            val session = platform.startScan(onDevice = { fail("No debe emitir un dispositivo") },
                onFailure = { failure = it })
            assertNotNull(failure)
            session.stop()
        }
        // No se inicia un escaneo de radio ni se asume soporte del emulador.
    }

    @Suppress("DEPRECATION")
    @Test fun manifestKeepsBleOptionalAllowsApiNetworkAndDoesNotAdvertise() {
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS)
        val feature = info.reqFeatures!!.first { it.name == PackageManager.FEATURE_BLUETOOTH_LE }
        assertEquals(0, feature.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED)
        val permissions = info.requestedPermissions!!.toList()
        assertTrue(permissions.contains(BlePermissionPolicy.SCAN))
        assertTrue(permissions.contains(BlePermissionPolicy.CONNECT))
        assertTrue(permissions.contains("android.permission.INTERNET")) // Required by the approved API integration.
        assertFalse(permissions.contains("android.permission.BLUETOOTH_ADVERTISE"))
        assertFalse(permissions.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
    }
}
