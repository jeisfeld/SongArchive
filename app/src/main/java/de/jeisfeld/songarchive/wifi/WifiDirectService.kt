package de.jeisfeld.songarchive.wifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.IBinder
import android.util.Log
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.ui.LyricsDisplayStyle

class WiFiDirectService : Service() {
    private lateinit var wifiHandler: WiFiDirectHandler
    private var mode = WifiMode.DISABLED
    private val TAG = "WiFiDirectService"
    private var isForegroundService = false
    private lateinit var lock: WifiLock

    override fun onCreate() {
        super.onCreate()
        wifiHandler = WiFiDirectHandler(applicationContext)

        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        lock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WifiDirectLock")
        lock.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        @Suppress("DEPRECATION")
        val action: WifiAction? = intent?.getSerializableExtra("ACTION") as WifiAction?
        Log.d(TAG, "Received message for action " + action)

        if (action == null) {
            return START_STICKY
        }
        if (WifiViewModel.wifiTransferMode == WifiMode.DISABLED && action != WifiAction.WIFI_DISABLE) {
            startNotification(intent, action)
            stopForeground(STOP_FOREGROUND_REMOVE)
            return START_STICKY
        }

        when (action) {
            WifiAction.WIFI_DISABLE -> {
                mode = WifiMode.DISABLED
                Log.d(TAG, "❌ Stopping Wi-Fi Direct Service")
                wifiHandler.stopServer() // ✅ Stop Server
                wifiHandler.stopClient() // ✅ Stop Client
                startNotification(intent, action)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            WifiAction.WIFI_SERVER -> {
                mode = WifiMode.SERVER
                Log.d(TAG, "🚀 Starting in SERVER mode")
                wifiHandler.registerReceiver()
                wifiHandler.startWiFiDirectServer()
                startNotification(intent, action)
            }
            WifiAction.WIFI_CLIENT -> {
                mode = WifiMode.CLIENT
                Log.d(TAG, "🔄 Starting in CLIENT mode")
                wifiHandler.registerReceiver()
                wifiHandler.discoverPeersAfterClear() // Use correct IP
                startNotification(intent, action)
            }
            WifiAction.DISPLAY_LYRICS -> {
                val songId = intent?.getStringExtra("SONG_ID")
                @Suppress("DEPRECATION")
                val style = (intent?.getSerializableExtra("STYLE") as LyricsDisplayStyle?) ?: LyricsDisplayStyle.REMOTE_DEFAULT
                if (songId != null) {
                    wifiHandler.startActivityInClients(songId, style)
                }
            }
            WifiAction.CLIENT_CONNECTED, WifiAction.CLIENTS_CONNECTED -> {
                startNotification(intent, action)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiHandler.unregisterReceiver()
        wifiHandler.stopServer()
        wifiHandler.stopClient()
        lock.release()
        WifiViewModel.wifiTransferMode = WifiMode.DISABLED
        Log.d(TAG, "🛑 WiFiDirectService Stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNotification(intent: Intent?, action: WifiAction) {
        val notification = createNotification(intent, action)
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (isForegroundService) {
            notificationManager.notify(2, notification)
        } else {
            startForeground(2, notification)
            isForegroundService = true
        }
    }

    private fun createNotification(intent: Intent?, action: WifiAction): Notification {
        Log.d(TAG, "Creating service notification - action: " + action)
        val channelId = "WiFiDirectServiceChannel"
        val channel = NotificationChannel(
            channelId, "Wi-Fi Direct Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val stopIntent = Intent(this, WiFiDirectService::class.java).apply {
            setAction(WifiAction.WIFI_DISABLE.toString())
            putExtra("ACTION", WifiAction.WIFI_DISABLE)
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val numberOfClients = intent?.getIntExtra("CLIENTS", 0)?:0

        val contentText = when (action) {
            WifiAction.WIFI_SERVER -> getString(R.string.notification_server_created)
            WifiAction.WIFI_CLIENT -> getString(R.string.notification_client_created)
            WifiAction.CLIENTS_CONNECTED -> resources.getQuantityString(R.plurals.notification_server_connected, numberOfClients, numberOfClients)
            WifiAction.CLIENT_CONNECTED -> getString(R.string.notification_client_connected)
            else -> getString(R.string.notification_unknown)
        }

        return Notification.Builder(this, channelId)
            .setContentTitle("Wi-Fi Direct Service")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_white)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }
}
