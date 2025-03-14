package de.jeisfeld.songarchive.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.audio.AudioPlayerService
import de.jeisfeld.songarchive.audio.PlaybackViewModel
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.ui.theme.AppColors
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAudioPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onStop: () -> Unit
) {
    val currentTime by PlaybackViewModel.currentProgress.collectAsState()
    val totalDuration by PlaybackViewModel.totalDuration.collectAsState()
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start =  dimensionResource(id = R.dimen.spacing_medium), end = dimensionResource(id = R.dimen.spacing_medium), top = dimensionResource(id = R.dimen.spacing_small)),
        colors = CardDefaults.cardColors(containerColor = AppColors.BackgroundShaded)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = dimensionResource(id = R.dimen.spacing_medium))
        ) {
            // Current Time
            Text(
                text = formatTime(currentTime),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextColor,
                modifier = Modifier.padding(end = dimensionResource(id = R.dimen.spacing_medium))
            )

            // Progress Bar (with Ball Indicator)
            Slider(
                value = if (totalDuration > 0) (currentTime.toFloat() / totalDuration) else 0f,
                onValueChange = { progress ->
                    val newPosition = (progress * totalDuration).toLong()
                    PlaybackViewModel.setProgress(newPosition)
                    val intent = Intent(context, AudioPlayerService::class.java).apply {
                        action = "SEEK"
                        putExtra("SEEK_POSITION", (progress * totalDuration).toLong())
                    }
                    context.startService(intent)
                },
                modifier = Modifier.weight(1f),
                thumb = {
                    Box(
                        modifier = Modifier
                            .background(AppColors.TextColor, shape = CircleShape)
                            .padding(1.dp)
                            .size(width = 1.dp, height = 15.dp)
                    )
                },
                track = {
                    SliderDefaults.Track(
                        sliderState = it,
                        modifier = Modifier.height(12.dp),
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 0.dp,
                        drawStopIndicator = null,
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.TextColor,
                            activeTrackColor = AppColors.TextColorLight,
                            inactiveTrackColor = AppColors.ForegroundVeryLight
                        )
                    )
                }
            )

            // Total Duration
            Text(
                text = formatTime(totalDuration),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextColor,
                modifier = Modifier.padding(start = dimensionResource(id = R.dimen.spacing_medium))
            )

            Spacer(modifier = Modifier.width(dimensionResource(id = R.dimen.spacing_medium)))

            song.mp3filename2?.takeIf { it.isNotBlank() }?.let {
                IconButton(modifier = Modifier.padding(end = dimensionResource(id = R.dimen.spacing_medium)).size(dimensionResource(id = R.dimen.icon_size_small)),
                    onClick = {
                        PlaybackViewModel.changeSong()
                        val filename = if (PlaybackViewModel.currentMp3Id.value == 0) { song.mp3filename } else { song.mp3filename2 }
                        val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString()).replace("+", "%20")
                        val mp3Url = "https://heilsame-lieder.de/audio/songs/$encodedFilename".toUri()
                        val intent = Intent(context, AudioPlayerService::class.java).apply {
                            action = "CHANGESONG"
                            putExtra("URL", mp3Url)
                        }
                        context.startForegroundService(intent)
                    }) {
                    Icon(
                        painter = painterResource(
                            id = R.drawable.ic_next
                        ),
                        contentDescription = "Next",
                        tint = AppColors.TextColor,
                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                    )
                }
            }

            // Play/Pause Toggle Button
            IconButton(modifier = Modifier.padding(end = dimensionResource(id = R.dimen.spacing_medium)).size(dimensionResource(id = R.dimen.icon_size_small)),
                onClick = onPlayPauseToggle) {
                Icon(
                    painter = painterResource(
                        id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = AppColors.TextColor,
                    modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                )
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val minutes = (ms / 1000) / 60
    val seconds = (ms / 1000) % 60
    return "%02d:%02d".format(minutes, seconds)
}

