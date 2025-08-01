package de.jeisfeld.songarchive.db

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import de.jeisfeld.songarchive.db.FavoriteListSong

class FavoriteListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).favoriteListDao()
    private val songDao = AppDatabase.getDatabase(application).songDao()

    val lists: StateFlow<List<FavoriteList>> = dao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    fun addList(name: String) {
        viewModelScope.launch { dao.insert(FavoriteList(name = name)) }
    }

    suspend fun addListWithSongs(name: String, songIds: List<String>): List<String> {
        val missing = mutableListOf<String>()
        val valid = mutableListOf<String>()
        songIds.forEach { id ->
            val exists = songDao.getSongById(id) != null
            if (exists) {
                valid.add(id)
            } else {
                missing.add(id)
            }
        }
        val listId = dao.insert(FavoriteList(name = name)).toInt()
        dao.insertSongs(valid.map { FavoriteListSong(listId, it) })
        return missing
    }

    fun rename(list: FavoriteList, newName: String) {
        viewModelScope.launch { dao.update(list.copy(name = newName)) }
    }

    fun delete(list: FavoriteList) {
        viewModelScope.launch { dao.delete(list) }
    }

    fun updateSongLists(songId: String, addIds: List<Int>, removeIds: List<Int>) {
        viewModelScope.launch {
            removeIds.forEach { id ->
                dao.deleteSongFromList(id, songId)
            }
            addIds.forEach { id ->
                dao.insertSong(FavoriteListSong(id, songId))
            }
        }
    }

    fun addSongToList(listId: Int, songId: String) {
        viewModelScope.launch {
            dao.insertSong(FavoriteListSong(listId, songId))
        }
    }

    fun removeSongFromList(listId: Int, songId: String) {
        viewModelScope.launch {
            dao.deleteSongFromList(listId, songId)
        }
    }

    suspend fun getSongsForList(listId: Int): List<Song> {
        return songDao.getSongsForList(listId)
    }

    suspend fun getListsForSong(songId: String): List<Int> {
        return dao.getListsForSong(songId)
    }
}
