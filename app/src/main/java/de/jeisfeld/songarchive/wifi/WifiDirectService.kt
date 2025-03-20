package de.jeisfeld.songarchive.wifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import de.jeisfeld.songarchive.R

class WiFiDirectService : Service() {
    private lateinit var wifiHandler: WiFiDirectHandler
    private var mode = 0 // 0 = Disabled, 1 = Client, 2 = Server
    private val TAG = "WiFiDirectService"
    private var isForegroundService = false

    override fun onCreate() {
        super.onCreate()
        wifiHandler = WiFiDirectHandler(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val intentMode = intent?.getIntExtra("MODE", 0) ?: 10
        Log.d(TAG, "Received message for mode " + mode)
        when (intentMode) {
            0 -> { // Disable Mode
                mode = intentMode
                Log.d(TAG, "❌ Stopping Wi-Fi Direct Service")
                wifiHandler.stopServer() // ✅ Stop Server
                wifiHandler.stopClient() // ✅ Stop Client
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            1 -> { // Server Mode
                mode = intentMode
                Log.d(TAG, "🚀 Starting in SERVER mode")
                wifiHandler.registerReceiver()
                wifiHandler.startWiFiDirectServer()
                startNotification(intent)
            }
            2 -> { // Client Mode
                mode = intentMode
                Log.d(TAG, "🔄 Starting in CLIENT mode")
                wifiHandler.registerReceiver()
                wifiHandler.discoverPeersAfterClear() // Use correct IP
                startNotification(intent)
            }
            10 -> {
                val songId = intent?.getStringExtra("SONG_ID")
                if (songId != null) {
                    wifiHandler.startActivityInClients(songId)
                }
            }
            11, 12 -> {
                startNotification(intent)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiHandler.unregisterReceiver()
        wifiHandler.stopServer()
        wifiHandler.stopClient()
        WifiViewModel.wifiTransferMode = 0
        Log.d(TAG, "🛑 WiFiDirectService Stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNotification(intent: Intent?) {
        val notification = createNotification(intent)
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (isForegroundService) {
            notificationManager.notify(2, notification)
        } else {
            startForeground(2, notification)
            isForegroundService = true
        }
    }

    private fun createNotification(intent: Intent?): Notification {
        Log.d(TAG, "Creating service notification - mode: " + mode)
        val channelId = "WiFiDirectServiceChannel"
        val channel = NotificationChannel(
            channelId, "Wi-Fi Direct Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val stopIntent = Intent(this, WiFiDirectService::class.java).apply {
            putExtra("MODE", 0)
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intentMode = intent?.getIntExtra("MODE", 0)
        val contentText = when (intentMode) {
            1 -> "Server created"
            2 -> "Client created"
            11 -> "Server connected to " + intent.getIntExtra("CLIENTS", 0) + " clients"
            12 -> "Connected as client"
            else -> "Unknown status"
        }

        return Notification.Builder(this, channelId)
            .setContentTitle("Wi-Fi Direct Service")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }
}
