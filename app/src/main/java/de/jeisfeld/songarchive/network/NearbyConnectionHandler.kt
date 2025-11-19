package de.jeisfeld.songarchive.network

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
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
    private val RECONNECT_DELAY_MS = 2000L
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val handler = Handler(Looper.getMainLooper())

    private var type = PeerConnectionMode.DISABLED
    private val connectedEndpoints = mutableSetOf<String>()
    private var reconnectRunnable: Runnable? = null

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
            scheduleClientReconnect()
        }
    }

    override fun sendCommandToClients(command: NetworkCommand, params: Map<String, String?>) {
        for (endpointId in connectedEndpoints) {
            sendCommandToClient(endpointId, command, params)
        }
    }

    private val gson = Gson()

    fun sendCommandToClient(endpointId: String, command: NetworkCommand, params: Map<String, String?> = emptyMap()) {
        val message = Message(command.name, params.filterValues { it != null }.mapValues { it.value!! })
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
        clearReconnectSchedule()
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
                clearReconnectSchedule()
                when (type) {
                    PeerConnectionMode.CLIENT -> {
                        Log.d(TAG, "Connected as client")
                        Toast.makeText(context, context.getString(R.string.toast_connected_as_client), Toast.LENGTH_SHORT).show()
                        updateNotification(PeerConnectionAction.CLIENT_CONNECTED)
                    }

                    PeerConnectionMode.SERVER -> {
                        Log.d(TAG, "Client connected")
                        Toast.makeText(context, context.getString(R.string.toast_client_connected, connectedEndpoints.size), Toast.LENGTH_SHORT)
                            .show()
                        updateNotification(PeerConnectionAction.CLIENTS_CONNECTED)
                        PeerConnectionViewModel.connectedDevices = connectedEndpoints.size
                        val lastSentCommand = PeerConnectionViewModel.lastSentCommand
                        if (lastSentCommand == null) {
                            sendCommandToClient(
                                endpointId, NetworkCommand.DISPLAY_TEXT, mapOf(
                                    "style" to DisplayStyle.REMOTE_BLACK.toString()
                                )
                            )
                        } else {
                            sendCommandToClient(endpointId, lastSentCommand.command, lastSentCommand.params)
                        }
                    }

                    PeerConnectionMode.DISABLED -> {}
                }
            } else if (type == PeerConnectionMode.CLIENT) {
                Log.w(TAG, "Connection failed with status ${result.status}")
                scheduleClientReconnect()
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            when (type) {
                PeerConnectionMode.CLIENT -> {
                    Log.d(TAG, "Disconnected as client")
                    Toast.makeText(context, context.getString(R.string.toast_disconnected_as_client), Toast.LENGTH_SHORT).show()
                    updateNotification(PeerConnectionAction.CLIENT_DISCONNECTED)
                    scheduleClientReconnect()
                }

                PeerConnectionMode.SERVER -> {
                    Log.d(TAG, "Client connected")
                    Toast.makeText(context, context.getString(R.string.toast_client_disconnected, connectedEndpoints.size), Toast.LENGTH_SHORT).show()
                    updateNotification(PeerConnectionAction.CLIENTS_CONNECTED)
                    PeerConnectionViewModel.connectedDevices = connectedEndpoints.size
                }

                PeerConnectionMode.DISABLED -> {}
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            connectionsClient.requestConnection(android.os.Build.MODEL, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Request connection failed: ${e.message}", e)
                    scheduleClientReconnect()
                }
        }

        override fun onEndpointLost(endpointId: String) {
            if (type == PeerConnectionMode.CLIENT) {
                scheduleClientReconnect()
            }
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
            NetworkCommand.DISPLAY_SONG, NetworkCommand.DISPLAY_CHORDS, NetworkCommand.DISPLAY_TEXT, NetworkCommand.DISPLAY_LYRICS -> {
                val songId = message.params?.get("songId") ?: ""
                val style = message.params?.get("style")?.let { DisplayStyle.valueOf(it) } ?: return
                if (PeerConnectionViewModel.clientMode == ClientMode.CHORDS && style == DisplayStyle.REMOTE_BLACK) return
                if (PeerConnectionViewModel.clientMode == ClientMode.CHORDS && NetworkCommand.valueOf(message.command) == NetworkCommand.DISPLAY_LYRICS) return
                if (PeerConnectionViewModel.clientMode != ClientMode.CHORDS && NetworkCommand.valueOf(message.command) == NetworkCommand.DISPLAY_CHORDS) return
                val lyrics = message.params.get("lyrics") ?: ""
                val lyricsShort = message.params.get("lyricsShort") ?: ""
                val intent = Intent().apply {
                    setClass(
                        context, when (PeerConnectionViewModel.clientMode) {
                            ClientMode.LYRICS_BS, ClientMode.LYRICS_BW, ClientMode.LYRICS_WB -> LyricsViewerActivity::class.java
                            ClientMode.CHORDS -> if (songId.isEmpty()) LyricsViewerActivity::class.java else ChordsViewerActivity::class.java
                        }
                    )
                    putExtra("STYLE", style)
                    if (songId.isNotEmpty()) putExtra("SONG_ID", songId)
                    if (lyrics.isNotEmpty()) putExtra("LYRICS", lyrics)
                    if (lyricsShort.isNotEmpty()) putExtra("LYRICS_SHORT", lyricsShort)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                context.startActivity(intent)

                val powerManager = getSystemService(context, PowerManager::class.java) as PowerManager
                if (!powerManager.isInteractive) {
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK or
                                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                                PowerManager.ON_AFTER_RELEASE,
                        "CircleSongArchive:wakeLock"
                    )
                    wakeLock.acquire(4000)
                    Thread.sleep(1000)
                    context.startActivity(intent)
                }
            }

            NetworkCommand.SHARE_FAVORITE_LIST -> {
                val name = message.params?.get("name") ?: return
                val ids = message.params?.get("songIds") ?: return
                val intent = Intent(context, de.jeisfeld.songarchive.ui.favoritelists.FavoriteListImportActivity::class.java).apply {
                    putExtra("LIST_NAME", name)
                    putExtra("SONG_IDS", ids)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            NetworkCommand.CLIENT_DISCONNECT -> {
                PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.DISABLED
                updateNotification(PeerConnectionAction.CONNECTION_DISABLE)
                PeerConnectionViewModel.stopRemoteActivity.postValue(true)
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

    private fun scheduleClientReconnect() {
        if (type != PeerConnectionMode.CLIENT) {
            return
        }

        val powerManager = getSystemService(context, PowerManager::class.java) as PowerManager
        if (!powerManager.isInteractive) {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "CircleSongArchive:clientReconnect"
            )
            wakeLock.acquire(30000)
        }

        reconnectRunnable?.let { handler.removeCallbacks(it) }
        val reconnectAction = Runnable {
            if (type == PeerConnectionMode.CLIENT && connectedEndpoints.isEmpty()) {
                Log.d(TAG, "Attempting to restart discovery after disconnect")
                connectionsClient.stopDiscovery()
                connectionsClient.stopAllEndpoints()
                startClient()
                reconnectRunnable?.let { handler.postDelayed(it, RECONNECT_DELAY_MS) }
            }
        }

        reconnectRunnable = reconnectAction
        handler.postDelayed(reconnectAction, RECONNECT_DELAY_MS)
    }

    private fun clearReconnectSchedule() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }
}

