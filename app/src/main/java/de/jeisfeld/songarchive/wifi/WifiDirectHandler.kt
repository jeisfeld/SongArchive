package de.jeisfeld.songarchive.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresPermission
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.ui.LyricsDisplayStyle
import de.jeisfeld.songarchive.ui.STOP_LYRICS_VIEWER_ACTIVITY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class WiFiDirectHandler(private val context: Context) {
    private val manager: WifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel = manager.initialize(context, context.mainLooper) { Log.d(TAG, "🛑 Channel disconnected") }
    private val receivers = mutableListOf<BroadcastReceiver>()
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    private val SERVICE_INSTANCE_NAME = "de.jeisfeld.songarchive.WIFI_SERVICE"
    private val SERVICE_TYPE = "_presence._tcp"
    private val TAG = "WiFiDirectHandler"
    private val PORT = 8869

    @Volatile
    private var isServerRunning = false
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isClientRunning = false
    private var clientSocket: Socket? = null
    private val connectedClients = mutableSetOf<String>() // Track connected clients
    private val clientSockets = mutableListOf<Socket>()
    private var isClientRetryThreadRunning = false

    fun registerReceiver() {
        synchronized(receivers) {
            receivers.forEach { context.unregisterReceiver(it) }
            receivers.clear()
            val newReceiver = WiFiDirectBroadcastReceiver(manager, channel, this)
            context.registerReceiver(newReceiver, intentFilter)
            receivers.add(newReceiver)
            Log.d(TAG, "Registered receiver " + receivers.size)
        }
    }

    fun unregisterReceiver() {
        synchronized(receivers) {
            receivers.forEach { context.unregisterReceiver(it) }
            receivers.clear()
            Log.d(TAG, "Unregistered receiver " + receivers.size)
        }
    }

    fun startServer() {
        isServerRunning = true

        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "✅ Server started, waiting for clients...")

                while (isServerRunning) {
                    try {
                        Log.d(TAG, "Waiting for client")
                        val clientSocket = serverSocket!!.accept()
                        val clientIp = clientSocket.inetAddress.hostAddress
                        Log.d(TAG, "Found client $clientIp")

                        clientIp?.let {
                            synchronized(connectedClients) {
                                if (connectedClients.contains(clientIp)) {
                                    Log.w(TAG, "⚠️ Duplicate connection attempt from $clientIp. Ignoring.")
                                    clientSocket.close()
                                } else {
                                    connectedClients.add(clientIp)
                                    synchronized(clientSockets) {
                                        clientSockets.add(clientSocket)
                                    }
                                    Log.d(TAG, "🔗 Client connected: $clientIp")
                                    CoroutineScope(Dispatchers.Main).launch {
                                        Toast.makeText(context, context.getString(R.string.toast_client_connected, clientSockets.size), Toast.LENGTH_SHORT).show()
                                    }
                                    val serviceIntent = Intent(context, WiFiDirectService::class.java).apply {
                                        setAction(WifiAction.CLIENTS_CONNECTED.toString())
                                        putExtra("ACTION", WifiAction.CLIENTS_CONNECTED)
                                        putExtra("CLIENTS", connectedClients.size)
                                    }
                                    WifiViewModel.connectedDevices = connectedClients.size
                                    context.startService(serviceIntent)
                                    startActivityInClient("", LyricsDisplayStyle.REMOTE_BLACK, clientSocket)
                                    handleClientDisconnect(clientSocket, clientIp)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (isServerRunning) {
                            Log.e(TAG, "❌ Error accepting client connection: ${e.message}")
                        }
                        else {
                            Log.d(TAG, "Error accepting client connection: ${e.message}")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "❌ Server error: ${e.message}")
            } finally {
                stopServer()
            }
        }.start()
    }

    fun handleClientDisconnect(clientSocket: Socket, clientIp: String) {
        Thread {
            try {
                val input = clientSocket.getInputStream()
                val buffer = ByteArray(1)

                while (isServerRunning && clientSocket.isConnected) {
                    if (input.read(buffer) == -1) {
                        throw IOException("Client disconnected")
                    }
                    Thread.sleep(2000) // ✅ Check every 2 seconds
                }
            } catch (e: IOException) {
                Log.w("WiFiDirectHandler", "⚠️ Client $clientIp disconnected: ${e.message}")
                synchronized(connectedClients) {
                    connectedClients.remove(clientIp)
                }
                synchronized(clientSockets) {
                    clientSockets.removeIf { it.inetAddress.hostAddress == clientIp }
                }
                Log.d("WiFiDirectHandler", "🛑 Removed client: $clientIp")
                if (isServerRunning) {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, context.getString(R.string.toast_client_disconnected, clientSockets.size), Toast.LENGTH_SHORT).show()
                    }
                }
                val serviceIntent = Intent(context, WiFiDirectService::class.java).apply {
                    setAction(WifiAction.CLIENTS_CONNECTED.toString())
                    putExtra("ACTION", WifiAction.CLIENTS_CONNECTED)
                    putExtra("CLIENTS", connectedClients.size)
                }
                WifiViewModel.connectedDevices = connectedClients.size
                context.startForegroundService(serviceIntent)
            }
        }.start()
    }

    fun startWiFiDirectServer() {
        if (isServerRunning) {
            stopServer()
        }
        // First, remove any existing group to ensure a clean start
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                Log.d(TAG, "✅ Removed existing Wi-Fi Direct group")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    createNewWiFiDirectGroup() // Call function to create a new group
                }
            }

            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onFailure(reason: Int) {
                Log.w(TAG, "⚠️ No existing group found or failed to remove. Continuing...")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    createNewWiFiDirectGroup() // Call function to create a new group
                }
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    private fun createNewWiFiDirectGroup() {
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                Log.d(TAG, "✅ Wi-Fi Direct group created! Server is now discoverable.")
                advertiseService()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "❌ Failed to create Wi-Fi Direct group. Error: $reason")
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
                Log.d(TAG, "✅ Service advertised: $SERVICE_INSTANCE_NAME")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "❌ Service advertisement failed! Error: $reason")
            }
        })
    }

    fun startActivityInClients(songId: String, style: LyricsDisplayStyle) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(clientSockets) {
                for (clientSocket in clientSockets) {
                    startActivityInClient(songId, style, clientSocket)
                }
            }
        }.start()
    }

    fun startActivityInClient(songId: String, style: LyricsDisplayStyle, clientSocket: Socket) {
        sendCommandToClient(WifiCommand.START_ACTIVITY,
            "de.jeisfeld.songarchive.ui.LyricsViewerActivity|" + style.name + "|" + songId,
            clientSocket
        )
    }

    fun triggerClientdisconnect(clientSocket: Socket) {
        sendCommandToClient(WifiCommand.CLIENT_DISCONNECT, "", clientSocket)
    }

    fun sendCommandToClient(command: WifiCommand, params: String, clientSocket: Socket) {
        try {
            val output = PrintWriter(clientSocket.getOutputStream(), true)
            output.println(command.name + "|" + params)
            Log.d(TAG, "📡 Sent command " + command + " with parameters " + params + " to client: ${clientSocket.inetAddress.hostName}")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to send command: ${e.message}")
        }
    }

    fun stopServer() {
        if (!isServerRunning) {
            return
        }
        isServerRunning = false // ❌ Stop the loop

        try {
            serverSocket?.close() // ✅ Close the server socket
            serverSocket = null
            synchronized(clientSockets) {
                for (clientSocket in clientSockets) {
                    CoroutineScope(Dispatchers.IO).launch {
                        triggerClientdisconnect(clientSocket)
                        clientSocket.close()
                    }
                }
                clientSockets.clear()
                connectedClients.clear()
                WifiViewModel.connectedDevices = 0
            }
            Log.d(TAG, "🛑 Server stopped")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to stop server: ${e.message}")
        }
    }

    fun discoverPeersAfterClear() {
        Log.d(TAG, "📡 Clearing old service requests...")

        clientSocket?.let {
            try {
                clientSocket!!.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close client socket: " + e.message)
            }
            clientSocket = null
        }

        // ✅ First, clear previous service requests to prevent conflicts
        manager.clearServiceRequests(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "✅ Cleared old service requests")
                discoverPeers()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "❌ Failed to clear old service requests! Error: $reason")
                discoverPeers()
            }
        })
    }

    fun discoverPeers() {
        Log.d(TAG, "📡 Starting Wi-Fi Direct service discovery...")

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                Log.d(TAG, "✅ Added service request")

                // ✅ Set response listeners BEFORE starting discovery
                manager.setDnsSdResponseListeners(channel,
                    { instanceName, registrationType, device ->
                        if (instanceName == SERVICE_INSTANCE_NAME) { // ✅ Match the correct service
                            Log.d(TAG, "✅ Found correct server: ${device.deviceName}")
                            connectToPeer(device) // Automatically connect to the correct server
                        } else {
                            Log.d(TAG, "⚠️ Found unknown service: $instanceName")
                        }
                    },
                    { fullDomainName, txtRecordMap, device ->
                        Log.d(TAG, "🔎 TXT Record from ${device.deviceName}, Data: $txtRecordMap")
                    }
                )

                CoroutineScope(Dispatchers.Main).launch {
                    delay(2000)
                    // ✅ Now start service discovery
                    manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d(TAG, "✅ Service discovery started...")
                            if (!isClientRetryThreadRunning) {
                                isClientRetryThreadRunning = true
                                CoroutineScope(Dispatchers.IO).launch {
                                    Log.d(TAG, "Triggering retry..")
                                    delay(30000)
                                    Log.d(TAG, "Doing retry..")
                                    isClientRetryThreadRunning = false
                                    if (!isClientRunning && clientSocket == null && WifiViewModel.wifiTransferMode == WifiMode.CLIENT) {
                                        Log.w(TAG, "⚠️ Failed to find service. Retrying...")
                                        discoverPeersAfterClear()
                                    }
                                }
                            }
                        }

                        override fun onFailure(reason: Int) {
                            Log.e(TAG, "❌ Service discovery failed: $reason")
                        }
                    })
                }
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "❌ Failed to add service request! Error: $reason")
            }
        })
    }

    fun connectToPeer(device: WifiP2pDevice) {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onSuccess() {
                Log.d(TAG, "✅ Removed existing group on client. Now connecting to server...")
                initiatePeerConnection(device)
            }

            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
            override fun onFailure(reason: Int) {
                Log.w(TAG, "⚠️ No existing group found on client. Continuing...")
                initiatePeerConnection(device)
            }
        })
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun initiatePeerConnection(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = 0
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "✅ Successfully requested connection to ${device.deviceName}")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed: $reason")
            }
        })
    }

    fun startClient(context: Context, serverIp: String) {
        if (isClientRunning) {
            Log.w(TAG, "⚠️ Client is already connected. Ignoring duplicate start request.")
            return
        }
        isClientRunning = true

        Log.d(TAG, "startClient")

        Thread {
            try {
                clientSocket = Socket(serverIp, PORT)
                val input = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, context.getString(R.string.toast_connected_as_client), Toast.LENGTH_SHORT).show()
                }
                val serviceIntent = Intent(context, WiFiDirectService::class.java).apply {
                    setAction(WifiAction.CLIENT_CONNECTED.toString())
                    putExtra("ACTION", WifiAction.CLIENT_CONNECTED)
                }
                context.startService(serviceIntent)


                while (isClientRunning) {
                    try {
                        val command = input.readLine()
                        processCommand(command)
                    } catch (e: IOException) {
                        if (isClientRunning) {
                            Log.e(TAG, "❌ Client connection error: ${e.message}", e)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "❌ Failed to connect to server: ${e.message}")
            } finally {
                stopClient()
            }
        }.start()
    }

    fun processCommand(commandString: String?) {
        commandString?.let {
            try {
                val parts = commandString.split("|")
                val command = WifiCommand.valueOf(parts[0])
                when (command) {
                    WifiCommand.START_ACTIVITY -> {
                        if (parts.size >= 4) {
                            val activityName = parts[1]
                            val style = LyricsDisplayStyle.valueOf(parts[2])
                            val songId = parts[3]

                            Log.d(TAG, "✅ Received activity command: $activityName, Style: $style, SongId: $songId")

                            val intent = Intent()
                            intent.setClassName(context, activityName)
                            intent.putExtra("STYLE", style)
                            if (songId.isNotEmpty()) intent.putExtra("SONG_ID", songId)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                        else {

                        }
                    }
                    WifiCommand.CLIENT_DISCONNECT -> {
                        WifiViewModel.wifiTransferMode = WifiMode.DISABLED
                        WifiViewModel.startWifiDirectService(context)
                        val intent = Intent(STOP_LYRICS_VIEWER_ACTIVITY)
                        context.sendBroadcast(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error when processing command " + commandString + ": " + e.message)
            }
        }
    }

    fun stopClient() {
        if (!isClientRunning) {
            return
        }
        isClientRunning = false // ❌ Stop the loop

        try {
            clientSocket?.close() // ✅ Close client socket
            clientSocket = null
            Log.d(TAG, "🛑 Client stopped")
        } catch (e: IOException) {
            Log.e(TAG, "❌ Failed to stop client: ${e.message}")
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    fun handleConnection(info: WifiP2pInfo) {
        if (info.groupFormed) {
            handleConnecton(info.isGroupOwner, info.groupOwnerAddress.hostAddress)
        } else {
            Log.e(TAG, "❌ Connection failed! No group formed.")
        }
    }

    fun handleConnecton(isServer: Boolean, serverIp: String?) {
        if (isServer) {
            if (!isServerRunning) {
                Log.d(TAG, "✅ Server is prepared. Starting TCP server...")
                startServer()
            }
        }
        else {
            Log.d(TAG, "✅ Connected to server at IP: $serverIp")
            serverIp?.let {
                startClient(context, serverIp)
            }
        }
    }

}

enum class WifiCommand {
    START_ACTIVITY,
    CLIENT_DISCONNECT
}
