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
}
