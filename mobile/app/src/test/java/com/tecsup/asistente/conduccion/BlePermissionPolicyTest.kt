package com.tecsup.asistente.conduccion

import com.tecsup.asistente.conduccion.core.permissions.BlePermissionPolicy
import org.junit.Assert.*
import org.junit.Test

class BlePermissionPolicyTest {
    @Test fun android26Through30RequireForegroundLocationOnly() {
        for (sdk in 26..30) {
            assertEquals(listOf(BlePermissionPolicy.FINE_LOCATION), BlePermissionPolicy.runtimePermissions(sdk))
            assertTrue(BlePermissionPolicy.requiresLocationService(sdk))
        }
    }

    @Test fun android31AndLaterRequireScanAndConnectWithoutLocation() {
        for (sdk in 31..35) {
            assertEquals(listOf(BlePermissionPolicy.SCAN, BlePermissionPolicy.CONNECT),
                BlePermissionPolicy.runtimePermissions(sdk))
            assertFalse(BlePermissionPolicy.requiresLocationService(sdk))
        }
    }
}
