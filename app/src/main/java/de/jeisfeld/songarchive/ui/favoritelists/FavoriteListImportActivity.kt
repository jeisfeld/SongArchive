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
import de.jeisfeld.songarchive.db.FavoriteListEntryInput

class FavoriteListImportActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listName = intent.getStringExtra("LIST_NAME") ?: ""
        val songIds = intent.getStringExtra("SONG_IDS")?.takeIf { it.isNotBlank() }?.split(',') ?: emptyList()
        val sorted = intent.getBooleanExtra("LIST_SORTED", false)
        val entriesJson = intent.getStringExtra("LIST_ENTRIES")
        val viewModel = ViewModelProvider(this)[FavoriteListViewModel::class.java]
        setContent {
            AppTheme {
                ImportDialog(listName, songIds, entriesJson, sorted, viewModel) { finish() }
            }
        }
    }
}

@Composable
private fun ImportDialog(name: String, songs: List<String>, entriesJson: String?, sorted: Boolean, viewModel: FavoriteListViewModel, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<String>?>(null) }
    val payloadEntries = remember(entriesJson) { parseEntries(entriesJson) }

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
                        val missingIds = if (payloadEntries != null) {
                            viewModel.addListWithEntries(name, payloadEntries, sorted)
                        } else {
                            viewModel.addListWithSongs(name, songs, sorted)
                        }
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

private fun parseEntries(json: String?): List<FavoriteListEntryInput>? {
    if (json.isNullOrBlank()) return null
    return try {
        val array = org.json.JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val songId = obj.optString("songId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val position = obj.optInt("position", index)
            val title = obj.optString("customTitle").takeIf { it.isNotBlank() }
            FavoriteListEntryInput(songId, position, title)
        }.sortedBy { it.position }
    } catch (_: Exception) {
        null
    }
}
