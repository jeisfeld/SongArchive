package de.jeisfeld.songarchive.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AudioPlayerService : Service() {
    private var song: Song? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var url: Uri
    private var isForegroundService = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        startNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                song = intent.getParcelableExtra("SONG")
                song?.mp3filename?.let { filename ->
                    PlaybackViewModel.firstSong()
                    val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString()).replace("+", "%20")
                    url = "https://heilsame-lieder.de/audio/songs/$encodedFilename".toUri()
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer.prepare()
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            sendPlaybackState()
                            if (isPlaying) {
                                startPlaybackUpdates()
                            }
                            startNotification()
                        }
                    })
                    exoPlayer.play()
                }
            }

            "CHANGESONG" -> {
                url = intent.getParcelableExtra("URL")!!
                exoPlayer.stop()
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                exoPlayer.play()
                sendPlaybackState()
            }

            "PAUSE" -> {
                exoPlayer.pause()
                sendPlaybackState()
            }

            "RESUME" -> {
                if (exoPlayer.currentPosition >= exoPlayer.duration) exoPlayer.seekTo(0L)
                exoPlayer.play()
                sendPlaybackState()
                startPlaybackUpdates()
            }

            "STOP" -> {
                song = null
                exoPlayer.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            "SEEK" -> {
                val position = intent.getLongExtra("SEEK_POSITION", 0L)
                exoPlayer.seekTo(position)
                sendPlaybackState()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "AUDIO_PLAYER_CHANNEL",
            "Audio Playback",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val playPauseAction = if (exoPlayer.isPlaying) {
            PendingIntent.getService(
                this, 1, Intent(this, AudioPlayerService::class.java).setAction("PAUSE"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, 2, Intent(this, AudioPlayerService::class.java).setAction("RESUME"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val stopAction = PendingIntent.getService(
            this, 3, Intent(this, AudioPlayerService::class.java).setAction("STOP"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("SONG", song)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "AUDIO_PLAYER_CHANNEL")
            .setContentTitle(song?.title ?: "Playing Audio")
            .setContentText(if (song?.author == null) "" else parseAuthors(song?.author?:""))
            .setSmallIcon(R.drawable.ic_launcher_white)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .addAction(
                if (exoPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (exoPlayer.isPlaying) "Pause" else "Play",
                playPauseAction
            )
            .addAction(R.drawable.ic_stop, "Stop", stopAction)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        exoPlayer.stop()
        exoPlayer.release()
        mediaSession.release()
        super.onDestroy()
    }

    private fun sendPlaybackState() {
        PlaybackViewModel.updatePlaybackState(
            song,
            exoPlayer.isPlaying,
            exoPlayer.currentPosition,
            Math.max(0L, exoPlayer.duration)
        )
    }

    private fun startNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (isForegroundService) {
            notificationManager.notify(1, notification)
        } else {
            startForeground(1, notification)
            isForegroundService = true
        }
    }

    private fun startPlaybackUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val isPlaying = withContext(Dispatchers.Main) { exoPlayer.isPlaying }
                if (!isPlaying) break

                withContext(Dispatchers.Main) {
                    sendPlaybackState()
                }
                delay(100)
            }
        }
    }

    fun parseAuthors(authors: String): String {
        val parts = authors.split(",")
        val builder = StringBuilder()

        parts.forEachIndexed { index, part ->
            val trimmed = part.trim()

            val fullRegex = Regex("(.+?)\\s*\\[(https?://)?([^]]+)]")

            when {
                fullRegex.matches(trimmed) -> {
                    val match = fullRegex.find(trimmed)!!
                    val name = match.groupValues[1].trim()
                    builder.append(name)
                }
                else -> {
                    builder.append(trimmed)
                }
            }

            if (index < parts.size - 1) builder.append(", ")
        }

        return builder.toString()
    }
}