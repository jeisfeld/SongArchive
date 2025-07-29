package de.jeisfeld.songarchive.ui.favoritelists

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.ui.theme.AppTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import de.jeisfeld.songarchive.R

class FavoriteListImportActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listName = intent.getStringExtra("LIST_NAME") ?: ""
        val songIds = intent.getStringExtra("SONG_IDS")?.takeIf { it.isNotBlank() }?.split(',') ?: emptyList()
        val viewModel = ViewModelProvider(this)[FavoriteListViewModel::class.java]
        setContent {
            AppTheme {
                ImportDialog(listName, songIds, viewModel) { finish() }
            }
        }
    }
}

@Composable
private fun ImportDialog(name: String, songs: List<String>, viewModel: FavoriteListViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<String>?>(null) }

    if (missing != null) {
        AlertDialog(
            onDismissRequest = { missing = null; onClose() },
            title = { Text(stringResource(id = R.string.import_favorite_list)) },
            text = { Text(stringResource(id = R.string.missing_songs, missing!!.joinToString(","))) },
            confirmButton = { TextButton(onClick = { missing = null; onClose() }) { Text(stringResource(id = R.string.ok)) } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(stringResource(id = R.string.import_favorite_list)) },
            text = { Text(stringResource(id = R.string.confirm_import_list, name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val missingIds = viewModel.addListWithSongs(name, songs)
                        if (missingIds.isNotEmpty()) {
                            missing = missingIds
                        } else {
                            onClose()
                        }
                    }
                }) { Text(stringResource(id = R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onClose) { Text(stringResource(id = R.string.cancel)) } }
        )
    }
}
