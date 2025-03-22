package de.jeisfeld.songarchive.network

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.ui.LyricsDisplayStyle
import de.jeisfeld.songarchive.ui.STOP_LYRICS_VIEWER_ACTIVITY
import java.nio.charset.StandardCharsets

class NearbyConnectionHandler(private val context: Context) : PeerConnectionHandler {
    private val TAG = "NearbyConnection"
    private val SERVICE_ID = "de.jeisfeld.songarchive.NEARBY_SERVICE"
    private val STRATEGY = Strategy.P2P_STAR
    private val connectionsClient = Nearby.getConnectionsClient(context)

    private var type = PeerConnectionMode.DISABLED
    private val connectedEndpoints = mutableSetOf<String>()

    override fun registerReceiver() { /* not needed */ }
    override fun unregisterReceiver() { /* not needed */ }

    override fun startServer() {
        type = PeerConnectionMode.SERVER
        connectionsClient.stopAdvertising()
        connectionsClient.startAdvertising(
            android.os.Build.MODEL,
            SERVICE_ID,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Advertising failed: ${e.message}", e)
        }
    }

    override fun startClient() {
        type = PeerConnectionMode.CLIENT
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed: ${e.message}", e)
        }
    }

    override fun sendCommandToClients(songId: String, style: LyricsDisplayStyle) {
        for (endpointId in connectedEndpoints) {
            sendCommandToClient(songId, style, endpointId)
        }
    }

    fun sendCommandToClient(songId: String, style: LyricsDisplayStyle, endpointId: String) {
        sendCommandToClient(NetworkCommand.START_ACTIVITY, "de.jeisfeld.songarchive.ui.LyricsViewerActivity|${style.name}|$songId", endpointId)
    }

    fun sendCommandToClient(command: NetworkCommand, params: String, endpointId: String) {
        val output = command.name + "|" + params
        val payload = Payload.fromBytes(output.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
        Log.d(TAG, "Sent command to endpoint " + endpointId + ": " + command)
    }

    override fun stopServer() {
        for (endpointId in connectedEndpoints) {
                triggerClientDisconnect(endpointId)
        }
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        type = PeerConnectionMode.DISABLED
    }

    fun triggerClientDisconnect(endpointId: String) {
        sendCommandToClient(NetworkCommand.CLIENT_DISCONNECT, "", endpointId)
    }

    override fun stopClient() {
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        type = PeerConnectionMode.DISABLED
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpoints.add(endpointId)
                when (type) {
                    PeerConnectionMode.CLIENT -> {
                        Log.d(TAG, "Connected as client")
                        Toast.makeText(context, context.getString(R.string.toast_connected_as_client), Toast.LENGTH_SHORT).show()
                        val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                            setAction(PeerConnectionAction.CLIENT_CONNECTED.toString())
                            putExtra("ACTION", PeerConnectionAction.CLIENT_CONNECTED)
                        }
                        context.startService(serviceIntent)
                    }
                    PeerConnectionMode.SERVER -> {
                        Log.d(TAG, "Client connected")
                        Toast.makeText(context, context.getString(R.string.toast_client_connected, connectedEndpoints.size), Toast.LENGTH_SHORT).show()
                        val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                            setAction(PeerConnectionAction.CLIENTS_CONNECTED.toString())
                            putExtra("ACTION", PeerConnectionAction.CLIENTS_CONNECTED)
                            putExtra("CLIENTS", connectedEndpoints.size)
                        }
                        PeerConnectionViewModel.connectedDevices = connectedEndpoints.size
                        context.startService(serviceIntent)
                        sendCommandToClient("", LyricsDisplayStyle.REMOTE_BLACK, endpointId)
                    }
                    PeerConnectionMode.DISABLED -> { }
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            when (type) {
                PeerConnectionMode.CLIENT -> {
                    Log.d(TAG, "Disconnected as client")
                    Toast.makeText(context, context.getString(R.string.toast_disconnected_as_client), Toast.LENGTH_SHORT).show()
                    val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                        setAction(PeerConnectionAction.CLIENT_CONNECTED.toString())
                        putExtra("ACTION", PeerConnectionAction.CLIENT_DISCONNECTED)
                    }
                    context.startService(serviceIntent)
                }
                PeerConnectionMode.SERVER -> {
                    Log.d(TAG, "Client connected")
                    Toast.makeText(context, context.getString(R.string.toast_client_disconnected, connectedEndpoints.size), Toast.LENGTH_SHORT).show()
                    val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                        setAction(PeerConnectionAction.CLIENTS_CONNECTED.toString())
                        putExtra("ACTION", PeerConnectionAction.CLIENTS_CONNECTED)
                        putExtra("CLIENTS", connectedEndpoints.size)
                    }
                    PeerConnectionViewModel.connectedDevices = connectedEndpoints.size
                    context.startForegroundService(serviceIntent)
                }
                PeerConnectionMode.DISABLED -> { }
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(android.os.Build.MODEL, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            // Handle if needed
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Log.d(TAG, "Processing payload " + payload)
            payload.asBytes()?.let {
                val message = String(it, StandardCharsets.UTF_8)
                processCommand(message)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op
        }
    }

    private fun processCommand(commandString: String) {
        val parts = commandString.split("|")
        when (parts[0]) {
            "START_ACTIVITY" -> {
                if (parts.size >= 4) {
                    val activityName = parts[1]
                    val style = LyricsDisplayStyle.valueOf(parts[2])
                    val songId = parts[3]

                    val intent = Intent().apply {
                        setClassName(context, activityName)
                        putExtra("STYLE", style)
                        if (songId.isNotEmpty()) putExtra("SONG_ID", songId)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            "CLIENT_DISCONNECT" -> {
                PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.DISABLED
                PeerConnectionViewModel.startPeerConnectionService(context)
                val intent = Intent(STOP_LYRICS_VIEWER_ACTIVITY)
                context.sendBroadcast(intent)
            }
        }
    }
}

