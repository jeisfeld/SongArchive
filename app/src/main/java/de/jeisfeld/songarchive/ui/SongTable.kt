package de.jeisfeld.songarchive.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.collectAsState
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.jeisfeld.songarchive.audio.AudioPlayerService
import de.jeisfeld.songarchive.audio.PlaybackViewModel
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SongTable(viewModel: SongViewModel, songs: List<Song>, isWideScreen: Boolean) {
    val context = LocalContext.current
    val currentlyPlayingSong by PlaybackViewModel.currentlyPlayingSong.collectAsState()
    val isPlaying by PlaybackViewModel.isPlaying.collectAsState()

    var displayedSongs = songs
    if (isPlaying) {
        val playingId = currentlyPlayingSong?.id
        var foundPlayingSong = false
        for (song in songs) {
            if (song.id == playingId) {
                foundPlayingSong = true
                continue
            }
        }
        if (! foundPlayingSong ) {
            displayedSongs = currentlyPlayingSong?.let { songs + it } ?: songs
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = dimensionResource(id = R.dimen.spacing_medium))) {
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
            items(displayedSongs) { song ->
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
                                    intent.putExtra("SONG", song)
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
                                            CoroutineScope(Dispatchers.IO).launch {
                                                val meanings = viewModel.getMeaningsForSong(song.id) // Fetch in IO thread
                                                withContext(Dispatchers.Main) {  // Switch back to Main thread to start Activity
                                                    val intent = Intent(context, ChordsViewerActivity::class.java).apply {
                                                        putExtra("SONG", song)
                                                        putExtra("MEANINGS", meanings)  // Ensure Meaning is Parcelable
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))
                            }

                            song.mp3filename?.takeIf { it.isNotBlank() }?.let { filename ->
                                Image(
                                    painter = painterResource(
                                        id = if (currentlyPlayingSong?.id == song.id) R.drawable.ic_stop else R.drawable.ic_play
                                    ),
                                    contentDescription = if (currentlyPlayingSong?.id == song.id) "Stop" else "Play MP3",
                                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small)).clickable {
                                        if (currentlyPlayingSong?.id == song.id) {
                                            val intent = Intent(context, AudioPlayerService::class.java).apply {
                                                action = "STOP"
                                            }
                                            context.startService(intent)
                                            PlaybackViewModel.updatePlaybackState(null, false, 0L, 0L)
                                        } else {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                (context as? Activity)?.let { activity ->
                                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                                        != PackageManager.PERMISSION_GRANTED
                                                    ) {
                                                        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                                                    }
                                                }
                                            }

                                            PlaybackViewModel.updatePlaybackState(song, true, 0L, 0L)
                                            val intent = Intent(context, AudioPlayerService::class.java).apply {
                                                action = "PLAY"
                                                putExtra("SONG", song) // Pass entire Song object
                                            }
                                            context.startForegroundService(intent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = AppColors.TextColorLight)

                    // Ensure Mini Player is displayed when the song is playing
                    if (currentlyPlayingSong?.id == song.id) {
                        MiniAudioPlayer(
                            song = song,
                            isPlaying = isPlaying,
                            onPlayPauseToggle = {
                                val intent = Intent(context, AudioPlayerService::class.java).apply {
                                    action = if (isPlaying) "PAUSE" else "RESUME"
                                }
                                context.startService(intent)
                                PlaybackViewModel.toggleIsPlaying()
                            },
                            onStop = {
                                val intent = Intent(context, AudioPlayerService::class.java).apply {
                                    action = "STOP"
                                }
                                context.startService(intent)
                                PlaybackViewModel.updatePlaybackState(null, false, 0L, 0L)
                            }
                        )
                    }
                }
            }
        }
    }
}


