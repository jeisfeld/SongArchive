package de.jeisfeld.songarchive.network

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

interface PeerConnectionHandler {
    fun startServer()
    fun startClient()
    fun stopEndpoint()
    fun sendCommandToClients(command: NetworkCommand, params: Map<String, String?> = emptyMap())
}

enum class NetworkCommand {
    DISPLAY_SONG,
    DISPLAY_CHORDS,
    DISPLAY_TEXT,
    DISPLAY_LYRICS,
    SHARE_FAVORITE_LIST,
    CLIENT_DISCONNECT
}

enum class DisplayStyle {
    STANDARD,
    REMOTE_DEFAULT,
    REMOTE_BLACK,
    LOCAL_PREVIEW
}

data class Message(
    val command: String,
    val params: Map<String, String>? = null
)

fun isNearbyConnectionPossible(context: Context): Boolean {
    // The menu must be available before permissions are granted or the radios are enabled.
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH) &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
}

fun isNearbyConnectionEnabled(context: Context): Boolean {
    if (missingNearbyPermissions(context).isNotEmpty()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        return false
    }
    val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val isBluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val isWifiEnabled = wifiManager.isWifiEnabled

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    return isBluetoothEnabled && isWifiEnabled && isLocationEnabled
}
