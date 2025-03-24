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
import de.jeisfeld.songarchive.ui.LyricsViewerActivity
import java.nio.charset.StandardCharsets

class NearbyConnectionHandler(private val context: Context) : PeerConnectionHandler {
    private val TAG = "NearbyConnection"
    private val SERVICE_ID = "de.jeisfeld.songarchive.NEARBY_SERVICE"
    private val STRATEGY = Strategy.P2P_STAR
    private val connectionsClient = Nearby.getConnectionsClient(context)

    private var type = PeerConnectionMode.DISABLED
    private val connectedEndpoints = mutableSetOf<String>()

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

    override fun sendCommandToClients(command: NetworkCommand, vararg params: String) {
        for (endpointId in connectedEndpoints) {
            sendCommandToClient(endpointId, command, *params)
        }
    }

    fun sendCommandToClient(endpointId: String, command: NetworkCommand, vararg params: String) {
        val jointParams = params.joinToString(separator = "|")
        val output = command.name + "|" + jointParams
        val payload = Payload.fromBytes(output.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
        Log.d(TAG, "Sent command to endpoint " + endpointId + ": " + output)
    }

    fun triggerClientDisconnect(endpointId: String) {
        sendCommandToClient(endpointId, NetworkCommand.CLIENT_DISCONNECT)
    }

    override fun stopEndpoint() {
        if (type == PeerConnectionMode.SERVER) {
            for (endpointId in connectedEndpoints) {
                triggerClientDisconnect(endpointId)
            }
        }
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
                        updateNotification(PeerConnectionAction.CLIENT_CONNECTED)
                    }
                    PeerConnectionMode.SERVER -> {
                        Log.d(TAG, "Client connected")
                        Toast.makeText(context, context.getString(R.string.toast_client_connected, connectedEndpoints.size), Toast.LENGTH_SHORT).show()
                        updateNotification(PeerConnectionAction.CLIENTS_CONNECTED)
                        PeerConnectionViewModel.connectedDevices = connectedEndpoints.size
                        sendCommandToClient(endpointId, NetworkCommand.DISPLAY_LYRICS, "", LyricsDisplayStyle.REMOTE_BLACK.toString())
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
                    updateNotification(PeerConnectionAction.CLIENT_DISCONNECTED)
                }
                PeerConnectionMode.SERVER -> {
                    Log.d(TAG, "Client connected")
                    Toast.makeText(context, context.getString(R.string.toast_client_disconnected, connectedEndpoints.size), Toast.LENGTH_SHORT).show()
                    updateNotification(PeerConnectionAction.CLIENTS_CONNECTED)
                    PeerConnectionViewModel.connectedDevices = connectedEndpoints.size
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
        Log.d(TAG, "Processing command: " + commandString)
        val parts = commandString.split("|")
        val command = NetworkCommand.valueOf(parts[0])
        when (command) {
            NetworkCommand.DISPLAY_LYRICS -> {
                if (parts.size >= 3) {
                    val songId = parts[1]
                    val style = LyricsDisplayStyle.valueOf(parts[2])

                    val intent = Intent().apply {
                        setClass(context, LyricsViewerActivity::class.java)
                        putExtra("STYLE", style)
                        if (songId.isNotEmpty()) putExtra("SONG_ID", songId)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            NetworkCommand.CLIENT_DISCONNECT -> {
                PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.DISABLED
                PeerConnectionViewModel.triggerStopLyrics()
            }
        }
    }

    fun updateNotification(action: PeerConnectionAction) {
        val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
            setAction(action.toString())
            putExtra("ACTION", action)
            putExtra("CLIENTS", connectedEndpoints.size)
        }
        context.startForegroundService(serviceIntent)
    }
}

