package de.jeisfeld.songarchive.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.ui.theme.AppTheme

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        setContent { AppTheme { SettingsScreen(viewModel) { finish() } } }
    }
}
