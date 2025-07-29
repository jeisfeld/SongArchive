package de.jeisfeld.songarchive.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jeisfeld.songarchive.db.AppDatabase
import de.jeisfeld.songarchive.db.AppMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import de.jeisfeld.songarchive.network.DefaultNetworkConnection

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).appMetadataDao()

    private val _language = MutableStateFlow("system")
    val language: StateFlow<String> = _language

    private val _defaultNetworkConnection = MutableStateFlow(DefaultNetworkConnection.NONE.id)
    val defaultNetworkConnection: StateFlow<Int> = _defaultNetworkConnection

    init {
        viewModelScope.launch {
            dao.observe().collect { meta ->
                meta?.let {
                    _language.value = it.language
                    _defaultNetworkConnection.value = it.defaultNetworkConnection
                }
            }
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        viewModelScope.launch {
            val current = dao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0, language = lang, defaultNetworkConnection = _defaultNetworkConnection.value)
            dao.insert(current.copy(language = lang))
        }
    }

    fun setDefaultNetworkConnection(value: Int) {
        _defaultNetworkConnection.value = value
        viewModelScope.launch {
            val current = dao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0, language = _language.value, defaultNetworkConnection = value)
            dao.insert(current.copy(defaultNetworkConnection = value))
        }
    }
}
