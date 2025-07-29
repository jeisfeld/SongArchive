package de.jeisfeld.songarchive.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.ui.theme.AppTheme
import de.jeisfeld.songarchive.db.SongViewModel

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[SongViewModel::class.java]
        setContent { AppTheme { SettingsScreen(viewModel) { finish() } } }
    }
}
