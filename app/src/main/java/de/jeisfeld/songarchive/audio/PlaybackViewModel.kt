package de.jeisfeld.songarchive.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import de.jeisfeld.songarchive.db.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object PlaybackViewModel : ViewModel() {
    private val _currentlyPlayingSong = MutableStateFlow<Song?>(null)
    val currentlyPlayingSong = _currentlyPlayingSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentProgress = MutableStateFlow(0L)
    val currentProgress = _currentProgress.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asStateFlow()

    private val _currentMp3Id = MutableStateFlow(0)
    val currentMp3Id = _currentMp3Id.asStateFlow()

    fun updatePlaybackState(song: Song?, isPlaying: Boolean, progress: Long, duration: Long) {
        _currentlyPlayingSong.update { song }
        _isPlaying.update { isPlaying }
        _currentProgress.update { progress }
        _totalDuration.update { duration }
    }

    fun toggleIsPlaying () {
        _isPlaying.update { !_isPlaying.value }
    }

    fun setProgress(progress: Long) {
        _currentProgress.update { progress }
    }

    fun changeSong() {
        _currentMp3Id.update { (currentMp3Id.value + 1) % 2 }
    }
    fun firstSong() {
        _currentMp3Id.update { 0 }
    }
}

fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}