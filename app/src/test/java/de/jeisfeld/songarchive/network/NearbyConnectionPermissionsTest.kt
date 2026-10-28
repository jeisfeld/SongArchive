package de.jeisfeld.songarchive.network

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyConnectionPermissionsTest {
    @Test
    fun android17RequestsLocalAccessTogetherWithDiscoveryPermissions() {
        val permissions = requiredNearbyPermissions(37)
        assertTrue(permissions.containsAll(listOf(
            Manifest.permission.ACCESS_LOCAL_NETWORK,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )))
    }

    @Test
    fun olderAndroidVersionsNeverRequestUnknownPermissions() {
        for (sdk in 26..36) {
            val permissions = requiredNearbyPermissions(sdk)
            assertFalse(permissions.contains(Manifest.permission.ACCESS_LOCAL_NETWORK))
            if (sdk < 33) assertFalse(permissions.contains(Manifest.permission.NEARBY_WIFI_DEVICES))
            if (sdk < 31) {
                assertFalse(permissions.any { it in bluetoothPermissions })
            }
        }
    }

    @Test
    fun android12IncludesBluetoothAndPairedLocationRequest() {
        for (sdk in 31..32) {
            val permissions = requiredNearbyPermissions(sdk)
            assertTrue(permissions.containsAll(bluetoothPermissions))
            assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
            assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    @Test
    fun android13Through16IncludeNearbyWifi() {
        for (sdk in 33..36) {
            assertTrue(requiredNearbyPermissions(sdk).contains(Manifest.permission.NEARBY_WIFI_DEVICES))
        }
    }

    @Test
    fun runtimeRequestOmitsInstallTimePermissions() {
        val installTimePermissions = setOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )
        for (sdk in 26..37) {
            assertFalse(requiredNearbyPermissions(sdk).any { it in installTimePermissions })
        }
    }

    private val bluetoothPermissions = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )
}
