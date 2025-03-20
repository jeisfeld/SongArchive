package de.jeisfeld.songarchive.wifi


import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.annotation.RequiresPermission

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val handler: WiFiDirectHandler
) : BroadcastReceiver() {
    private val TAG = "WifiDirectBroadcastReceiver"

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Log.d(TAG, "Wi-Fi Direct is enabled")
                } else {
                    Log.d(TAG, "Wi-Fi Direct is disabled")
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager.requestPeers(channel) { peers ->
                    val deviceNames = peers.deviceList.map { it.deviceName }
                    Log.d(TAG, "Available peers: $deviceNames")
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                if (networkInfo?.isConnected == true && context != null) {
                    manager.requestConnectionInfo(channel) { info ->
                        Log.d(TAG, "connectionInfo: $info")
                        handler.handleConnection(info)
                    }
                } else {
                    Log.w(TAG, "❌ Wi-Fi Direct connection lost! Retrying discovery...")

                    // ✅ Trigger reconnection attempt
                    if (WifiViewModel.wifiTransferMode == 2) {
                        handler.discoverPeersAfterClear()
                    }
                }
            }
        }
    }
}
