package de.jeisfeld.songarchive.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class WiFiDirectHandler(private val context: Context) {
    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel = manager.initialize(context, context.mainLooper, null)
    private var receiver: BroadcastReceiver? = null
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    private val clientSockets = mutableListOf<Socket>()
    private val SERVICE_INSTANCE_NAME = "de.jeisfeld.songarchive.WIFI_SERVICE"
    private val SERVICE_TYPE = "_presence._tcp"

    fun registerReceiver() {
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver() {
        receiver?.let { context.unregisterReceiver(it) }
    }

    private fun startServer() {
        Thread {
            try {
                val serverSocket = ServerSocket(8888)
                Log.d("WiFiDirectHandler", "✅ Server started, waiting for clients...")

                while (true) {
                    val clientSocket = serverSocket.accept() // Accept client connection
                    synchronized(clientSockets) {
                        clientSockets.add(clientSocket) // ✅ Store the client socket for later use
                    }
                    Log.d("WiFiDirectHandler", "🔗 Client connected: ${clientSocket.inetAddress}")
                }
            } catch (e: IOException) {
                Log.e("WiFiDirectHandler", "❌ Server error: ${e.message}")
            }
        }.start()
    }

    fun startWiFiDirectServer() {
        // First, remove any existing group to ensure a clean start
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                Log.d("WiFiDirectHandler", "✅ Removed existing Wi-Fi Direct group")
                createNewWiFiDirectGroup() // Call function to create a new group
            }

            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onFailure(reason: Int) {
                Log.w("WiFiDirectHandler", "⚠️ No existing group found or failed to remove. Continuing...")
                createNewWiFiDirectGroup() // Still try to create a new group
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    private fun createNewWiFiDirectGroup() {
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                Log.d("WiFiDirectHandler", "✅ Wi-Fi Direct group created! Server is now discoverable.")
                // 🚀 Start advertising service for the app
                advertiseService()
            }

            override fun onFailure(reason: Int) {
                Log.e("WiFiDirectHandler", "❌ Failed to create Wi-Fi Direct group. Error: $reason")
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun advertiseService() {
        val record = mutableMapOf("SERVICE_NAME" to SERVICE_INSTANCE_NAME)

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            SERVICE_INSTANCE_NAME,
            SERVICE_TYPE,
            record
        )

        manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WiFiDirectHandler", "✅ Service advertised: $SERVICE_INSTANCE_NAME")
            }

            override fun onFailure(reason: Int) {
                Log.e("WiFiDirectHandler", "❌ Service advertisement failed! Error: $reason")
            }
        })
    }

    fun startActivityInClients(songId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(clientSockets) {
                for (clientSocket in clientSockets) {
                    try {
                        val output = PrintWriter(clientSocket.getOutputStream(), true)
                        output.println("START_ACTIVITY|de.jeisfeld.songarchive.ui.LyricsViewerActivity|" + songId)
                        Log.d("WiFiDirectHandler", "📡 Sent command to client: ${clientSocket.inetAddress}")
                    } catch (e: IOException) {
                        Log.e("WiFiDirectHandler", "❌ Failed to send command: ${e.message}")
                    }
                }
            }
        }.start()
    }

    fun discoverPeersAfterClear(context: Context) {
        Log.d("WiFiDirectHandler", "📡 Clearing old service requests...")

        // ✅ First, clear previous service requests to prevent conflicts
        manager.clearServiceRequests(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WiFiDirectHandler", "✅ Cleared old service requests")
                discoverPeers(context)
            }

            override fun onFailure(reason: Int) {
                Log.e("WiFiDirectHandler", "❌ Failed to clear old service requests! Error: $reason")
                discoverPeers(context)
            }
        })
    }

    fun discoverPeers(context: Context) {
        Log.d("WiFiDirectHandler", "📡 Starting Wi-Fi Direct service discovery...")

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                Log.d("WiFiDirectHandler", "✅ Added service request")

                // ✅ Set response listeners BEFORE starting discovery
                manager.setDnsSdResponseListeners(channel,
                    { instanceName, registrationType, device ->
                        if (instanceName == SERVICE_INSTANCE_NAME) { // ✅ Match the correct service
                            Log.d("WiFiDirectHandler", "✅ Found correct server: ${device.deviceName}")
                            connectToPeer(device) // Automatically connect to the correct server
                        } else {
                            Log.d("WiFiDirectHandler", "⚠️ Found unknown service: $instanceName")
                        }
                    },
                    { fullDomainName, txtRecordMap, device ->
                        Log.d("WiFiDirectHandler", "🔎 TXT Record from ${device.deviceName}, Data: $txtRecordMap")
                    }
                )

                // ✅ Now start service discovery
                manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d("WiFiDirectHandler", "✅ Service discovery started...")
                    }

                    override fun onFailure(reason: Int) {
                        Log.e("WiFiDirectHandler", "❌ Service discovery failed: $reason")
                    }
                })
            }

            override fun onFailure(reason: Int) {
                Log.e("WiFiDirectHandler", "❌ Failed to add service request! Error: $reason")
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WiFiDirectHandler", "Connected to server!")
            }

            override fun onFailure(reason: Int) {
                Log.e("WiFiDirectHandler", "Connection failed: $reason")
            }
        })
    }

    fun startClient(context: Context, serverIp: String) {
        Log.d("WiFiDirectHandler", "startClient")
        Thread {
            try {
                val socket = Socket(serverIp, 8888)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (true) {
                    val command = input.readLine()
                    if (command != null && command.startsWith("START_ACTIVITY")) {
                        val parts = command.split("|")
                        if (parts.size >= 3) {
                            val activityName = parts[1]
                            val params = parts[2]

                            Log.d("WiFiDirectHandler", "✅ Received activity command: $activityName, Params: $params")

                            val intent = Intent()
                            intent.setClassName(context, activityName)
                            intent.putExtra("SONG_ID", params)
                            context.startActivity(intent)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("WiFiDirectHandler", "❌ Client error: ${e.message}")
            }
        }.start()
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun handleConnection(info: WifiP2pInfo, context: Context) {
        if (info.groupFormed) {
            if (info.isGroupOwner) {
                // ✅ This device is the server (group owner)
                Log.d("WiFiDirectHandler", "✅ Server is connected to clients! Starting TCP server...")
                startServer() // 🚀 Start the TCP server
            } else {
                // ✅ This device is a client
                val serverIp = info.groupOwnerAddress.hostAddress
                Log.d("WiFiDirectHandler", "✅ Connected to server at IP: $serverIp")
                serverIp?.let {
                    startClient(context, serverIp) // 🚀 Connect to the server
                }
            }
        } else {
            Log.e("WiFiDirectHandler", "❌ Connection failed! No group formed.")
        }
    }

}
