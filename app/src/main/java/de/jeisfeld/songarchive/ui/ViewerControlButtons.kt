package de.jeisfeld.songarchive.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.audio.AudioPlayerService
import de.jeisfeld.songarchive.audio.PlaybackViewModel
import de.jeisfeld.songarchive.audio.isInternetAvailable
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.db.Meaning
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.network.DisplayStyle
import de.jeisfeld.songarchive.network.PeerConnectionAction
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionService
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.ui.favoritelists.AddToFavoriteListDialog

@Composable
fun ViewerControlButtons(
    showButtons: Boolean,
    isShowingLyrics: Boolean,
    song: Song?,
    displayStyle: DisplayStyle,
    meanings: List<Meaning> = emptyList(),
    onShowMeaningChange: (Boolean) -> Unit,
    onDisplayLyricsPage: (String?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val favoriteViewModel = ViewModelProvider(context as ViewModelStoreOwner)[FavoriteListViewModel::class.java]
    val favoriteLists by favoriteViewModel.lists.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var hasSentLyrics by remember { mutableStateOf(false) }
    var currentChunk by remember { mutableStateOf<String?>(null) }

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
                if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.SERVER && PeerConnectionViewModel.connectedDevices > 0
                    && displayStyle == DisplayStyle.STANDARD
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.3f))
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .size(dimensionResource(id = R.dimen.icon_size_large))
                            .clip(RoundedCornerShape(50))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        song?.let {
                                            val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                                                if (hasSentLyrics || currentChunk == null) {
                                                    setAction(PeerConnectionAction.DISPLAY_SONG.toString())
                                                    putExtra("ACTION", PeerConnectionAction.DISPLAY_SONG)
                                                    putExtra("SONG_ID", song.id)
                                                    putExtra("STYLE", if (hasSentLyrics) DisplayStyle.REMOTE_BLACK else DisplayStyle.REMOTE_DEFAULT)
                                                    putExtra("LYRICS", song.lyrics)
                                                    putExtra("LYRICS_SHORT", song.lyricsShort)
                                                }
                                                else {
                                                    setAction(PeerConnectionAction.DISPLAY_LYRICS.toString())
                                                    putExtra("ACTION", PeerConnectionAction.DISPLAY_LYRICS)
                                                    putExtra("STYLE", DisplayStyle.REMOTE_DEFAULT)
                                                    putExtra("LYRICS", currentChunk)
                                                }
                                            }
                                            context.startService(serviceIntent)
                                        }
                                        hasSentLyrics = !hasSentLyrics
                                    },
                                    onLongPress = {
                                        song?.let {
                                            val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                                                setAction(PeerConnectionAction.DISPLAY_CHORDS.toString())
                                                putExtra("ACTION", PeerConnectionAction.DISPLAY_CHORDS)
                                                putExtra("SONG_ID", song.id)
                                                putExtra("STYLE", DisplayStyle.REMOTE_DEFAULT)
                                            }
                                            context.startService(serviceIntent)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (hasSentLyrics) R.drawable.ic_send_black else R.drawable.ic_send),
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                        )
                    }
                    Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                }

                if (displayStyle == DisplayStyle.STANDARD && (hasSentLyrics || isShowingLyrics)) {
                    val lyricsPaged = song?.lyricsPaged
                    if (lyricsPaged != null) {
                        val chunks = listOf(null as String?) + lyricsPaged.split('|').map { it.trim() }

                        chunks.forEachIndexed { index, chunk ->
                            val circledNumber = when (index) {
                                0 -> "\u24EA" // ⓪ full lyrics
                                in 1..20 -> (0x2460 + index - 1).toChar().toString() // ① to ⑳
                                else -> index.toString() // fallback
                            }

                            IconButton(
                                onClick = {
                                    currentChunk = chunk
                                    onDisplayLyricsPage(chunk)

                                    if (hasSentLyrics) {
                                        val serviceIntent = Intent(context, PeerConnectionService::class.java).apply {
                                            setAction(PeerConnectionAction.DISPLAY_LYRICS.toString())
                                            putExtra("ACTION", PeerConnectionAction.DISPLAY_LYRICS)
                                            putExtra("STYLE", DisplayStyle.REMOTE_DEFAULT)
                                            if (index == 0) {
                                                putExtra("SONG_ID", song.id)
                                                putExtra("LYRICS", song.lyrics)
                                                putExtra("LYRICS_SHORT", song.lyricsShort)                                            }
                                            else {
                                                putExtra("LYRICS", chunk)
                                            }
                                        }
                                        context.startService(serviceIntent)
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
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Transparent)
                                ) {
                                    Text(
                                        text = circledNumber,
                                        color = Color.Black,
                                        fontSize = dimensionResource(id = R.dimen.icon_font_large).value.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                    }
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
                if (favoriteLists.isNotEmpty() && song != null) {
                    IconButton(
                        onClick = { showAddDialog = true },
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
                            painter = painterResource(id = R.drawable.ic_list),
                            contentDescription = "Favorites",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(dimensionResource(id = R.dimen.icon_size_small))
                        )
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

    if (showAddDialog && song != null) {
        AddToFavoriteListDialog(
            lists = favoriteLists,
            onAdd = { favoriteViewModel.addSongToLists(song.id, it) },
            onDismiss = { showAddDialog = false }
        )
    }
}
