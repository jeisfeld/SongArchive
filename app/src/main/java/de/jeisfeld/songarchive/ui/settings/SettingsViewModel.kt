package de.jeisfeld.songarchive.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jeisfeld.songarchive.db.AppDatabase
import de.jeisfeld.songarchive.db.AppMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).appMetadataDao()

    private val _language = MutableStateFlow("system")
    val language: StateFlow<String> = _language

    init {
        viewModelScope.launch {
            _language.value = dao.get()?.language ?: "system"
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        viewModelScope.launch {
            val current = dao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0, language = lang)
            dao.insert(current.copy(language = lang))
        }
    }
}
