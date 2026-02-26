package de.jeisfeld.songarchive.db

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import de.jeisfeld.songarchive.db.FavoriteListSong

data class FavoriteListEntryInput(val songId: String, val position: Int, val customTitle: String?)

class FavoriteListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).favoriteListDao()
    private val songDao = AppDatabase.getDatabase(application).songDao()

    val lists: StateFlow<List<FavoriteList>> = dao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    fun addList(name: String, sorted: Boolean = false) {
        viewModelScope.launch {
            val maxPosition = dao.getMaxListPosition()
            dao.insert(FavoriteList(name = name, isSorted = sorted, position = maxPosition + 1))
        }
    }

    suspend fun addListWithSongs(name: String, songIds: List<String>, sorted: Boolean = false): List<String> {
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
        val maxPosition = dao.getMaxListPosition()
        val listId = dao.insert(FavoriteList(name = name, isSorted = sorted, position = maxPosition + 1)).toInt()
        dao.insertSongs(valid.mapIndexed { index, songId -> FavoriteListSong(listId, songId, index) })
        return missing
    }

    suspend fun addListWithEntries(name: String, entries: List<FavoriteListEntryInput>, sorted: Boolean): List<String> {
        val missing = mutableListOf<String>()
        val validEntries = mutableListOf<FavoriteListSong>()
        entries.sortedBy { it.position }.forEach { entry ->
            val exists = songDao.getSongById(entry.songId) != null
            if (exists) {
                validEntries.add(FavoriteListSong(0, entry.songId, entry.position, entry.customTitle))
            } else {
                missing.add(entry.songId)
            }
        }
        val maxPosition = dao.getMaxListPosition()
        val listId = dao.insert(FavoriteList(name = name, isSorted = sorted, position = maxPosition + 1)).toInt()
        dao.insertSongs(validEntries.map { it.copy(listId = listId) })
        return missing
    }


    suspend fun getListByName(name: String): FavoriteList? {
        return dao.getByName(name)
    }

    suspend fun overwriteListWithSongs(listId: Int, sorted: Boolean, songIds: List<String>): List<String> {
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
        val existing = dao.getById(listId) ?: return missing
        dao.update(existing.copy(isSorted = sorted))
        dao.deleteAllSongsFromList(listId)
        dao.insertSongs(valid.mapIndexed { index, songId -> FavoriteListSong(listId, songId, index) })
        return missing
    }

    suspend fun overwriteListWithEntries(listId: Int, sorted: Boolean, entries: List<FavoriteListEntryInput>): List<String> {
        val missing = mutableListOf<String>()
        val validEntries = mutableListOf<FavoriteListSong>()
        entries.sortedBy { it.position }.forEach { entry ->
            val exists = songDao.getSongById(entry.songId) != null
            if (exists) {
                validEntries.add(FavoriteListSong(listId, entry.songId, entry.position, entry.customTitle))
            } else {
                missing.add(entry.songId)
            }
        }
        val existing = dao.getById(listId) ?: return missing
        dao.update(existing.copy(isSorted = sorted))
        dao.deleteAllSongsFromList(listId)
        dao.insertSongs(validEntries)
        return missing
    }

    fun rename(list: FavoriteList, newName: String, sorted: Boolean = list.isSorted) {
        viewModelScope.launch { dao.update(list.copy(name = newName, isSorted = sorted)) }
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
                val max = dao.getMaxPosition(id)
                dao.insertSong(FavoriteListSong(id, songId, max + 1))
            }
        }
    }

    suspend fun addSongToList(listId: Int, songId: String) {
        val max = dao.getMaxPosition(listId)
        dao.insertSong(FavoriteListSong(listId, songId, max + 1))
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

    suspend fun getSongEntries(listId: Int): List<FavoriteListSongWithSong> {
        return dao.getSongEntries(listId)
    }

    suspend fun getList(listId: Int): FavoriteList? {
        return dao.getById(listId)
    }

    fun updatePositions(listId: Int, orderedSongIds: List<String>) {
        viewModelScope.launch {
            orderedSongIds.forEachIndexed { index, songId ->
                dao.updatePosition(listId, songId, index)
            }
        }
    }

    fun updateListPositions(orderedListIds: List<Int>) {
        viewModelScope.launch {
            orderedListIds.forEachIndexed { index, listId ->
                dao.updateListPosition(listId, index)
            }
        }
    }

    fun updateCustomTitle(listId: Int, songId: String, customTitle: String?) {
        viewModelScope.launch { dao.updateCustomTitle(listId, songId, customTitle?.takeIf { it.isNotBlank() }) }
    }
}
