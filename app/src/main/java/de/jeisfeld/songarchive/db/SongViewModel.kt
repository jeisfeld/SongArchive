package de.jeisfeld.songarchive.db

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application, viewModelScope).songDao()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    init {
        loadAllSongs()
    }

    fun loadAllSongs() {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongs()  // Runs in background thread
        }
    }

    fun searchSongs(query: String) {
        viewModelScope.launch {
            _songs.value = songDao.searchSongs(query)  // Runs in background thread
        }
    }

}
