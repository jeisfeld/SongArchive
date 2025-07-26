package de.jeisfeld.songarchive.db

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun rename(list: FavoriteList, newName: String) {
        viewModelScope.launch { dao.update(list.copy(name = newName)) }
    }

    fun delete(list: FavoriteList) {
        viewModelScope.launch { dao.delete(list) }
    }

    fun addSongToLists(songId: String, listIds: List<Int>) {
        viewModelScope.launch {
            listIds.forEach { id ->
                dao.insertSong(FavoriteListSong(id, songId))
            }
        }
    }

    suspend fun getSongsForList(listId: Int): List<Song> {
        return songDao.getSongsForList(listId)
    }
}
