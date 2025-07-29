package de.jeisfeld.songarchive.ui.favoritelists

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.ui.theme.AppTheme

class FavoriteListsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[FavoriteListViewModel::class.java]
        setContent { AppTheme { FavoriteListsScreen(viewModel) { finish() } } }
    }
}
