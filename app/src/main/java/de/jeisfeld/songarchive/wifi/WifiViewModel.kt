package de.jeisfeld.songarchive.wifi

import android.content.Context
import androidx.lifecycle.ViewModel

object WifiViewModel : ViewModel() {
    var wifiHandler: WiFiDirectHandler? = null
    var wifiTransferMode = 0

    fun unregisterReceiver() {
        wifiHandler?.unregisterReceiver()
        wifiHandler = null
    }

    fun registerReceiver(context: Context) {
        wifiHandler?.unregisterReceiver()
        val handler = WiFiDirectHandler(context)
        wifiHandler = handler
        handler.registerReceiver()
    }

    fun discoverPeers(context: Context) {
        if (wifiHandler == null) {
            registerReceiver(context)
        }
        wifiHandler?.discoverPeersAfterClear(context)
    }

    fun startServer(context: Context) {
        if (wifiHandler == null) {
            registerReceiver(context)
        }
        wifiHandler?.startWiFiDirectServer()
    }

    fun startActivityInClients(songId: String) {
        wifiHandler?.startActivityInClients(songId)
    }
}