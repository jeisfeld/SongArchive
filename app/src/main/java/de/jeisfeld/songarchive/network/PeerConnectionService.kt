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
import de.jeisfeld.songarchive.ui.LyricsDisplayStyle

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
            return START_STICKY
        }

        when (action) {
            PeerConnectionAction.CONNECTION_DISABLE -> {
                mode = PeerConnectionMode.DISABLED
                Log.d(TAG, "❌ Stopping Peer Connection Service")
                peerConnectionHandler.stopServer() // ✅ Stop Server
                peerConnectionHandler.stopClient() // ✅ Stop Client
                startNotification(intent, action)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_STICKY
            }
            PeerConnectionAction.START_SERVER -> {
                mode = PeerConnectionMode.SERVER
                Log.d(TAG, "🚀 Starting in SERVER mode")
                peerConnectionHandler.registerReceiver()
                peerConnectionHandler.startServer()
            }
            PeerConnectionAction.START_CLIENT -> {
                mode = PeerConnectionMode.CLIENT
                Log.d(TAG, "🔄 Starting in CLIENT mode")
                peerConnectionHandler.registerReceiver()
                peerConnectionHandler.startClient() // Use correct IP
            }
            PeerConnectionAction.DISPLAY_LYRICS -> {
                val songId = intent?.getStringExtra("SONG_ID")
                @Suppress("DEPRECATION")
                val style = (intent?.getSerializableExtra("STYLE") as LyricsDisplayStyle?) ?: LyricsDisplayStyle.REMOTE_DEFAULT
                if (songId != null) {
                    peerConnectionHandler.sendCommandToClients(songId, style)
                }
            }
            PeerConnectionAction.CLIENT_CONNECTED, PeerConnectionAction.CLIENT_DISCONNECTED,
            PeerConnectionAction.CLIENTS_CONNECTED -> {
            }
        }

        startNotification(intent, action)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        peerConnectionHandler.unregisterReceiver()
        peerConnectionHandler.stopServer()
        peerConnectionHandler.stopClient()
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
            else -> getString(R.string.notification_unknown)
        }

        return Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.network_connection))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_white)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }
}
