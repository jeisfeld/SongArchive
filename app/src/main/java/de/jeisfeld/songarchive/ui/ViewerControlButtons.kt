package de.jeisfeld.songarchive.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.audio.AudioPlayerService
import de.jeisfeld.songarchive.audio.PlaybackViewModel
import de.jeisfeld.songarchive.audio.isInternetAvailable
import de.jeisfeld.songarchive.db.Meaning
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.network.DisplayStyle
import de.jeisfeld.songarchive.network.PeerConnectionAction
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionService
import de.jeisfeld.songarchive.network.PeerConnectionViewModel

@Composable
fun ViewerControlButtons(
    showButtons: Boolean,
    song: Song?,
    displayStyle: DisplayStyle,
    meanings: List<Meaning> = emptyList(),
    onShowMeaningChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var sendBlackScreen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        AnimatedVisibility(
            visible = showButtons,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row {
                if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.SERVER && PeerConnectionViewModel.connectedDevices > 0) {
                    IconButton(
                        onClick = {
                            song?.let {
                                val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                                    setAction(PeerConnectionAction.DISPLAY_LYRICS.toString())
                                    putExtra("ACTION", PeerConnectionAction.DISPLAY_LYRICS)
                                    putExtra("SONG_ID", song.id)
                                    putExtra("STYLE", if (sendBlackScreen) DisplayStyle.REMOTE_BLACK else DisplayStyle.REMOTE_DEFAULT)
                                    putExtra("LYRICS", song.lyrics)
                                    putExtra("LYRICS_SHORT", song.lyricsShort)
                                }
                                context.startService(serviceIntent)
                                sendBlackScreen = !sendBlackScreen
                            }
                        },
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .size(dimensionResource(id = R.dimen.icon_size_large))
                            .clip(RoundedCornerShape(50))
                    ) {
                        Icon(
                            painter = painterResource(id = if (sendBlackScreen) R.drawable.ic_send_black else R.drawable.ic_send),
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(dimensionResource(id = R.dimen.icon_size_small))
                        )
                    }
                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                }
                if (displayStyle == DisplayStyle.STANDARD && isInternetAvailable(context) && song?.mp3filename != null) {
                    val isCurrentSong = PlaybackViewModel.currentlyPlayingSong.collectAsState().value?.id == song.id
                    val isPlaying = PlaybackViewModel.isPlaying.collectAsState().value
                    IconButton(
                        onClick = {
                            if (isCurrentSong) {
                                val intent = Intent(context, AudioPlayerService::class.java).apply {
                                    action = if (isPlaying) "PAUSE" else "RESUME"
                                }
                                context.startService(intent)
                                PlaybackViewModel.toggleIsPlaying()
                            } else {
                                PlaybackViewModel.updatePlaybackState(song, true, 0L, 0L)
                                val intent = Intent(context, AudioPlayerService::class.java).apply {
                                    action = "PLAY"
                                    putExtra("SONG", song)
                                }
                                context.startForegroundService(intent)
                            }
                        },
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .size(dimensionResource(id = R.dimen.icon_size_large))
                            .clip(RoundedCornerShape(50))
                    ) {
                        Icon(
                            painter = painterResource(id = if (isCurrentSong && isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = if (isCurrentSong && isPlaying) "PAUSE" else "PLAY",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(dimensionResource(id = R.dimen.icon_size_small))
                        )
                    }
                    if (isCurrentSong) {
                        IconButton(
                            onClick = {
                                val intent = Intent(context, AudioPlayerService::class.java).apply {
                                    action = "STOP"
                                }
                                context.startService(intent)
                                PlaybackViewModel.updatePlaybackState(null, false, 0L, 0L)
                            },
                            modifier = Modifier
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                                    ),
                                    shape = RoundedCornerShape(50)
                                )
                                .size(dimensionResource(id = R.dimen.icon_size_large))
                                .clip(RoundedCornerShape(50))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_stop),
                                contentDescription = "STOP",
                                tint = Color.Black,
                                modifier = Modifier
                                    .size(dimensionResource(id = R.dimen.icon_size_small))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                }
                if (!meanings.isEmpty()) {
                    IconButton(
                        onClick = { onShowMeaningChange(true) },
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .size(dimensionResource(id = R.dimen.icon_size_large))
                            .clip(RoundedCornerShape(50))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = "Info",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(dimensionResource(id = R.dimen.icon_size_small))
                        )
                    }
                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(50)
                        )
                        .size(dimensionResource(id = R.dimen.icon_size_large))
                        .clip(RoundedCornerShape(50))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = Color.Black,
                        modifier = Modifier.padding(8.dp).size(dimensionResource(id = R.dimen.icon_size_small))
                    )
                }
            }
        }
    }
}
