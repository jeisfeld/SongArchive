package de.jeisfeld.songarchive.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Dangerous permissions only; Wi-Fi state and legacy Bluetooth permissions are install-time grants. */
@SuppressLint("InlinedApi") // New permission strings are guarded by the supplied Android version.
fun requiredNearbyPermissions(sdkInt: Int): List<String> = buildList {
    // Keep location access for Nearby discovery. Android 12+ requires requesting both together.
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (sdkInt >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    if (sdkInt >= 37) {
        add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
}

fun missingNearbyPermissions(context: Context): List<String> =
    requiredNearbyPermissions(Build.VERSION.SDK_INT).filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
