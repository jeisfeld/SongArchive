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
import com.google.gson.Gson
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.ui.ChordsViewerActivity
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

    override fun sendCommandToClients(command: NetworkCommand, params: Map<String, String>) {
        for (endpointId in connectedEndpoints) {
            sendCommandToClient(endpointId, command, params)
        }
    }

    private val gson = Gson()

    fun sendCommandToClient(endpointId: String, command: NetworkCommand, params: Map<String, String> = emptyMap()) {
        val message = Message(command.name, params)
        val json = gson.toJson(message)
        val payload = Payload.fromBytes(json.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(endpointId, payload)
        Log.d(TAG, "Sent JSON message to $endpointId: $json")
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
                        sendCommandToClient(endpointId, NetworkCommand.DISPLAY_SONG, mapOf(
                            "style" to DisplayStyle.REMOTE_BLACK.toString()
                        ))
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
            payload.asBytes()?.let {
                val json = String(it, StandardCharsets.UTF_8)
                Log.d(TAG, "Processing payload " + json)
                val message = gson.fromJson(json, Message::class.java)
                processCommand(message)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op
        }
    }

    private fun processCommand(message: Message) {
        when (NetworkCommand.valueOf(message.command)) {
            NetworkCommand.DISPLAY_SONG -> {
                val songId = message.params?.get("songId") ?: ""
                val style = message.params?.get("style")?.let { DisplayStyle.valueOf(it) } ?: return
                val intent = Intent().apply {
                    setClass(context, when (PeerConnectionViewModel.clientMode) {
                        ClientMode.LYRICS -> LyricsViewerActivity::class.java
                        ClientMode.CHORDS -> ChordsViewerActivity::class.java
                    })
                    putExtra("STYLE", style)
                    if (songId.isNotEmpty()) putExtra("SONG_ID", songId)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
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

