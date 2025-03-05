package de.jeisfeld.songarchive.db

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jeisfeld.songarchive.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application).songDao()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    init {
        loadAllSongs()
    }

    fun loadAllSongs() {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongs()
        }
    }

    fun searchSongs(query: String) {
        viewModelScope.launch {
            _songs.value = songDao.searchSongs(query)
        }
    }

    fun synchronizeDatabase(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val fetchedSongs = RetrofitClient.api.fetchSongs()
                songDao.clearSongs()
                songDao.insertSongs(fetchedSongs)
                _songs.value = fetchedSongs
                onComplete(true)
            } catch (e: Exception) {
                Log.e("SongArchive", "Failed to download song information", e)
                onComplete(false)
            }
        }
    }
}
