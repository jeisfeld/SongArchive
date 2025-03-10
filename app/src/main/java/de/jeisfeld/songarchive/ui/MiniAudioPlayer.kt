package de.jeisfeld.songarchive.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAudioPlayer(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    viewModel: SongViewModel,
    onStop: () -> Unit
) {
    val currentTime by viewModel.currentProgress
    var totalDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            val duration = exoPlayer.duration.coerceAtLeast(0L)
            val position = exoPlayer.currentPosition

            viewModel.currentProgress.value = position
            totalDuration = duration

            // Check if playback has reached the end
            if (duration > 0 && position >= duration) {
                viewModel.isPlaying.value = false
                exoPlayer.pause()
                //exoPlayer.seekTo(0L)
            }

            delay(100)
        }
    }

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
                    viewModel.currentProgress.value = newPosition // Update UI immediately
                    exoPlayer.seekTo(newPosition) // Seek to new position
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

            // Play/Pause Toggle Button
            IconButton(onClick = onPlayPauseToggle) {
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

