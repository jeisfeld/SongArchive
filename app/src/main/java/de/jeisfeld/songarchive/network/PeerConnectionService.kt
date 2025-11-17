package de.jeisfeld.songarchive.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import de.jeisfeld.songarchive.R

class PeerConnectionService : Service() {
    private lateinit var peerConnectionHandler: PeerConnectionHandler
    private var mode = PeerConnectionMode.DISABLED
    private val TAG = "PeerconnectionService"
    private var isForegroundService = false

    override fun onCreate() {
        super.onCreate()
        peerConnectionHandler = NearbyConnectionHandler(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val action: PeerConnectionAction? = intent?.getSerializableExtra("ACTION") as PeerConnectionAction?
        Log.d(TAG, "Received message for action " + action)

        if (action == null) {
            return START_STICKY
        }
        if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.DISABLED && action != PeerConnectionAction.CONNECTION_DISABLE) {
            startNotification(intent, action)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_STICKY
        }

        when (action) {
            PeerConnectionAction.CONNECTION_DISABLE -> {
                mode = PeerConnectionMode.DISABLED
                PeerConnectionViewModel.lastSentCommand = null
                Log.d(TAG, "❌ Stopping Peer Connection Service")
                peerConnectionHandler.stopEndpoint()
                startNotification(intent, action)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_STICKY
            }
            PeerConnectionAction.START_SERVER -> {
                mode = PeerConnectionMode.SERVER
                Log.d(TAG, "🚀 Starting in SERVER mode")
                startNotification(intent, action)
                peerConnectionHandler.startServer()
            }
            PeerConnectionAction.START_CLIENT -> {
                mode = PeerConnectionMode.CLIENT
                Log.d(TAG, "🔄 Starting in CLIENT mode")
                startNotification(intent, action)
                peerConnectionHandler.startClient() // Use correct IP
            }
            PeerConnectionAction.DISPLAY_LYRICS, PeerConnectionAction.DISPLAY_TEXT, PeerConnectionAction.DISPLAY_SONG -> {
                @Suppress("DEPRECATION")
                val style = (intent.getSerializableExtra("STYLE") as DisplayStyle?) ?: DisplayStyle.REMOTE_DEFAULT
                val songId = intent.getStringExtra("SONG_ID")
                val lyrics = intent.getStringExtra("LYRICS")
                val lyricsShort = intent.getStringExtra("LYRICS_SHORT")
                val networkCommand = when(action) {
                    PeerConnectionAction.DISPLAY_LYRICS -> NetworkCommand.DISPLAY_LYRICS
                    PeerConnectionAction.DISPLAY_TEXT -> NetworkCommand.DISPLAY_TEXT
                    else -> NetworkCommand.DISPLAY_SONG
                }
                if (songId != null || lyrics != null) {
                    val params = mapOf(
                        "songId" to songId,
                        "style" to style.toString(),
                        "lyrics" to lyrics,
                        "lyricsShort" to lyricsShort
                    )
                    PeerConnectionViewModel.lastSentCommand = LastSentCommand(networkCommand, params)
                    peerConnectionHandler.sendCommandToClients(networkCommand, params)
                }
            }
            PeerConnectionAction.DISPLAY_CHORDS -> {
                @Suppress("DEPRECATION")
                val style = (intent.getSerializableExtra("STYLE") as DisplayStyle?) ?: DisplayStyle.REMOTE_DEFAULT
                val songId = intent.getStringExtra("SONG_ID")
                if (songId != null) {
                    val params = mapOf(
                        "songId" to songId,
                        "style" to style.toString(),
                    )
                    PeerConnectionViewModel.lastSentCommand = LastSentCommand(NetworkCommand.DISPLAY_CHORDS, params)
                    peerConnectionHandler.sendCommandToClients(NetworkCommand.DISPLAY_CHORDS, params)
                }
            }
            PeerConnectionAction.SHARE_FAVORITE_LIST -> {
                val listName = intent.getStringExtra("LIST_NAME")
                val songIds = intent.getStringExtra("SONG_IDS")
                if (listName != null && songIds != null) {
                    val params = mapOf(
                        "name" to listName,
                        "songIds" to songIds
                    )
                    PeerConnectionViewModel.lastSentCommand = LastSentCommand(NetworkCommand.SHARE_FAVORITE_LIST, params)
                    peerConnectionHandler.sendCommandToClients(NetworkCommand.SHARE_FAVORITE_LIST, params)
                }
            }
            PeerConnectionAction.CLIENT_CONNECTED, PeerConnectionAction.CLIENT_DISCONNECTED,
            PeerConnectionAction.CLIENTS_CONNECTED -> {
                startNotification(intent, action)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        peerConnectionHandler.stopEndpoint()
        PeerConnectionViewModel.lastSentCommand = null
        PeerConnectionViewModel.peerConnectionMode = PeerConnectionMode.DISABLED
        Log.d(TAG, "🛑 PeerConnectionService Stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNotification(intent: Intent?, action: PeerConnectionAction) {
        val notification = createNotification(intent, action)
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (isForegroundService) {
            notificationManager.notify(2, notification)
        } else {
            startForeground(2, notification)
            isForegroundService = true
        }
    }

    private fun createNotification(intent: Intent?, action: PeerConnectionAction): Notification {
        Log.d(TAG, "Creating service notification - action: " + action)
        val channelId = "PeerConnectionServiceChannel"
        val channel = NotificationChannel(
            channelId, "Peer Connection Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val stopIntent = Intent(this, PeerConnectionService::class.java).apply {
            setAction(PeerConnectionAction.CONNECTION_DISABLE.toString())
            putExtra("ACTION", PeerConnectionAction.CONNECTION_DISABLE)
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val numberOfClients = intent?.getIntExtra("CLIENTS", 0)?:0

        val contentText = when (action) {
            PeerConnectionAction.START_SERVER -> getString(R.string.notification_server_created)
            PeerConnectionAction.START_CLIENT -> getString(R.string.notification_client_created)
            PeerConnectionAction.CLIENTS_CONNECTED -> resources.getQuantityString(R.plurals.notification_server_connected, numberOfClients, numberOfClients)
            PeerConnectionAction.CLIENT_CONNECTED -> getString(R.string.notification_client_connected)
            PeerConnectionAction.CLIENT_DISCONNECTED -> getString(R.string.notification_client_disconnected)
            else -> getString(R.string.notification_unknown, numberOfClients)
        }

        return Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.network_connection))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_white)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }
}
