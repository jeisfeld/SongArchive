package de.jeisfeld.songarchive.ui.favoritelists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FavoriteListSongsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val songViewModel = ViewModelProvider(this)[SongViewModel::class.java]
        val favViewModel = ViewModelProvider(this)[FavoriteListViewModel::class.java]
        val listId = intent.getIntExtra("LIST_ID", 0)
        val listName = intent.getStringExtra("LIST_NAME") ?: ""
        songViewModel.searchQuery.value = ""
        songViewModel.searchSongs("")
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) { favViewModel.getSongsForList(listId) }
            setContent {
                AppTheme {
                    FavoriteListSongsScreen(
                        songViewModel,
                        favViewModel,
                        listId,
                        listName,
                        songs
                    ) { finish() }
                }
            }
        }
    }
}
