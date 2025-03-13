package de.jeisfeld.songarchive.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, exoPlayer).build()

        startForeground(
            1,
            createNotification("Audio Player Running")
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                song = intent.getParcelableExtra("SONG")
                song?.mp3filename?.let { filename ->
                    val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString()).replace("+", "%20")
                    url = "https://heilsame-lieder.de/audio/songs/$encodedFilename".toUri()
                    exoPlayer.stop()
                    exoPlayer.setMediaItem(MediaItem.fromUri(url))
                    exoPlayer.prepare()
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state != Player.STATE_BUFFERING) sendPlaybackState()
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                startPlaybackUpdates()
                            }
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
                exoPlayer.play()
                sendPlaybackState()
                startPlaybackUpdates()
            }
            "STOP" -> {
                song = null
                exoPlayer.stop()
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

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "AUDIO_PLAYER_CHANNEL")
            .setContentTitle("Playing Audio")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_play)
            .setOngoing(true)
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

}