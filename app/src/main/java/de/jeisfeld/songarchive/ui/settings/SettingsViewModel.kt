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
    private val _defaultConnectionType = MutableStateFlow(0)
    val defaultConnectionType: StateFlow<Int> = _defaultConnectionType

    init {
        viewModelScope.launch {
            val metadata = dao.get()
            _language.value = metadata?.language ?: "system"
            _defaultConnectionType.value = metadata?.defaultConnectionType ?: 0
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        viewModelScope.launch {
            val current = dao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0, language = lang, defaultConnectionType = _defaultConnectionType.value)
            dao.insert(current.copy(language = lang))
        }
    }

    fun setDefaultConnectionType(type: Int) {
        _defaultConnectionType.value = type
        viewModelScope.launch {
            val current = dao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0, language = _language.value, defaultConnectionType = type)
            dao.insert(current.copy(defaultConnectionType = type))
        }
    }
}
