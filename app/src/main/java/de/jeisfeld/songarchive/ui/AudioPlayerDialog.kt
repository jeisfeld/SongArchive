package de.jeisfeld.songarchive.ui

import android.net.Uri
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun AudioPlayerDialog(mp3Url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(mp3Url)))
            prepare()
        }
    }

    AlertDialog(
        onDismissRequest = {
            exoPlayer.release() // Stop playback when dismissed
            onDismiss()
        },
        title = { Text("Playing MP3") },
        text = {
            Box(modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        val playerView = PlayerView(ctx)
                        playerView.player = exoPlayer
                        playerView.layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                        playerView
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                exoPlayer.release()
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}