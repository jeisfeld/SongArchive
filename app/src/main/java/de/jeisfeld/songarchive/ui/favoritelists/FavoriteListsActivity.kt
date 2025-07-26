package de.jeisfeld.songarchive.ui.favoritelists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.ui.theme.AppTheme

class FavoriteListsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[FavoriteListViewModel::class.java]
        setContent { AppTheme { FavoriteListsScreen(viewModel) { finish() } } }
    }
}
