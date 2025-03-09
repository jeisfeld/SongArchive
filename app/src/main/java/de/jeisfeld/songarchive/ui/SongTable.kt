package de.jeisfeld.songarchive.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun SongTable(viewModel: SongViewModel, songs: List<Song>, isWideScreen: Boolean) {
    val context = LocalContext.current
    val currentlyPlayingSong by viewModel.currentlyPlayingSong
    val isPlaying by viewModel.isPlaying
    val exoPlayer = viewModel.getExoPlayer(context)

    // Restore progress after rotation
    LaunchedEffect(viewModel.currentlyPlayingSong.value) {
        viewModel.currentlyPlayingSong.value?.let { url ->
            exoPlayer.setMediaItem(MediaItem.fromUri(url))
            exoPlayer.prepare()
            exoPlayer.seekTo(viewModel.currentProgress.value) // Restore progress
            exoPlayer.playWhenReady = viewModel.isPlaying.value
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Table Header (Outside LazyColumn)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = dimensionResource(id = R.dimen.spacing_small))) {
            Text(
                text = stringResource(id = R.string.column_id),
                modifier = Modifier.width(dimensionResource(id = R.dimen.width_id)),
                fontWeight = FontWeight.Bold,
                color = AppColors.TextColor
            )
            Text(
                text = stringResource(id = R.string.column_title),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                color = AppColors.TextColor
            )
            if (isWideScreen) {
                Text(
                    text = stringResource(id = R.string.column_author),
                    modifier = Modifier.weight(0.6f),
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextColor
                )
            }
            Text(
                text = stringResource(id = R.string.column_actions),
                modifier = Modifier.width(dimensionResource(id = R.dimen.width_actions)),
                fontWeight = FontWeight.Bold,
                color = AppColors.TextColor
            )
        }
        HorizontalDivider(color = AppColors.TextColorLight, thickness = 2.dp)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs) { song ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = dimensionResource(id = R.dimen.spacing_small)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = dimensionResource(id = R.dimen.spacing_small)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = song.id, modifier = Modifier.width(dimensionResource(id = R.dimen.width_id)), color = AppColors.TextColor)
                        Text(text = song.title, modifier = Modifier.weight(1f), color = AppColors.TextColor)
                        if (isWideScreen) {
                            Text(text = song.author ?: "", modifier = Modifier.weight(0.6f), color = AppColors.TextColor, fontStyle = FontStyle.Italic, fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        }
                        Row(modifier = Modifier.width(dimensionResource(id = R.dimen.width_actions))) {
                            Image(
                                painter = painterResource(id = R.drawable.text2),
                                contentDescription = stringResource(id = R.string.view_lyrics),
                                modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small)).clickable {
                                    val intent = Intent(context, LyricsViewerActivity::class.java)
                                    intent.putExtra("LYRICS", song.lyrics)
                                    context.startActivity(intent)
                                }
                            )
                            Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))

                            song.tabfilename?.takeIf { it.isNotBlank() }?.let {
                                Image(
                                    painter = painterResource(id = R.drawable.chords2),
                                    contentDescription = stringResource(id = R.string.view_chords),
                                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small)).clickable {
                                        val imageFile = File(context.filesDir, "chords/$it")
                                        if (imageFile.exists()) {
                                            val intent = Intent(context, ChordsViewerActivity::class.java)
                                            intent.putExtra("IMAGE_PATH", imageFile.absolutePath)
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                            }

                            song.mp3filename?.takeIf { it.isNotBlank() }?.let { filename ->
                                val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString()).replace("+", "%20")
                                val mp3Url = "https://heilsame-lieder.de/audio/songs/$encodedFilename"

                                Image(
                                    painter = painterResource(
                                        id = if (currentlyPlayingSong == mp3Url) R.drawable.ic_stop else R.drawable.ic_play
                                    ),
                                    contentDescription = if (currentlyPlayingSong == mp3Url) "Stop" else "Play MP3",
                                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small)).clickable {
                                        if (currentlyPlayingSong == mp3Url) {
                                            viewModel.releaseExoPlayer()
                                            viewModel.currentlyPlayingSong.value = null
                                            viewModel.isPlaying.value = false
                                        } else {
                                            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(mp3Url)))
                                            exoPlayer.prepare()
                                            exoPlayer.playWhenReady = true
                                            viewModel.currentlyPlayingSong.value = mp3Url
                                            viewModel.isPlaying.value = true
                                            viewModel.currentProgress.value = 0L
                                        }
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = AppColors.TextColorLight)

                    // Ensure Mini Player is displayed when the song is playing
                    if (currentlyPlayingSong == "https://heilsame-lieder.de/audio/songs/${URLEncoder.encode(song.mp3filename ?: "", StandardCharsets.UTF_8.toString()).replace("+", "%20")}") {
                        MiniAudioPlayer(
                            exoPlayer = exoPlayer,
                            isPlaying = isPlaying,
                            onPlayPauseToggle = {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                    viewModel.isPlaying.value = false
                                } else {
                                    if (exoPlayer.currentPosition >= exoPlayer.duration) {
                                        exoPlayer.seekTo(0)
                                    }
                                    exoPlayer.play()
                                    viewModel.isPlaying.value = true
                                }
                            },
                            onStop = {
                                viewModel.releaseExoPlayer()
                                viewModel.currentlyPlayingSong.value = null
                                viewModel.isPlaying.value = false
                                viewModel.currentProgress.value = 0L
                            },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}


