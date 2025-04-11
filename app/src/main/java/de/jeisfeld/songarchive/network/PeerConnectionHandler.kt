package de.jeisfeld.songarchive.network

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager

interface PeerConnectionHandler {
    fun startServer()
    fun startClient()
    fun stopEndpoint()
    fun sendCommandToClients(command: NetworkCommand, params: Map<String, String?> = emptyMap())
}

enum class NetworkCommand {
    DISPLAY_SONG,
    CLIENT_DISCONNECT
}

enum class DisplayStyle {
    STANDARD,
    REMOTE_DEFAULT,
    REMOTE_BLACK
}

data class Message(
    val command: String,
    val params: Map<String, String>? = null
)

fun isNearbyConnectionPossible(context: Context): Boolean {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val isBluetoothEnabled = bluetoothAdapter != null && bluetoothAdapter.isEnabled

    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val isWifiEnabled = wifiManager.isWifiEnabled

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    return isBluetoothEnabled && isWifiEnabled && isLocationEnabled
}
