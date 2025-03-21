package de.jeisfeld.songarchive.wifi

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel

object WifiViewModel : ViewModel() {
    var wifiTransferMode = WifiMode.DISABLED
    var connectedDevices = 0

    fun startWifiDirectService(context: Context) {
        val serviceIntent = Intent(context, WiFiDirectService::class.java).apply {
            val action = when (wifiTransferMode) {
                WifiMode.DISABLED -> WifiAction.WIFI_DISABLE
                WifiMode.SERVER -> WifiAction.WIFI_SERVER
                WifiMode.CLIENT -> WifiAction.WIFI_CLIENT
            }
            setAction(action.toString())
            putExtra("ACTION", action)
        }
        context.startForegroundService(serviceIntent)
    }
}


enum class WifiMode {
    DISABLED,
    SERVER,
    CLIENT
}

enum class WifiAction {
    WIFI_DISABLE,
    WIFI_SERVER,
    WIFI_CLIENT,
    DISPLAY_LYRICS,
    CLIENTS_CONNECTED,
    CLIENT_CONNECTED
}
