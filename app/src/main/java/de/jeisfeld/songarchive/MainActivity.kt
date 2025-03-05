package de.jeisfeld.songarchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: SongViewModel = ViewModelProvider(this)[SongViewModel::class.java]
            MainScreen(viewModel)
        }
    }
}

@Composable
fun MainScreen(viewModel: SongViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val songs by viewModel.songs.collectAsState()
    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600

    var showDialog by remember { mutableStateOf(false) }
    var syncSuccess by remember { mutableStateOf<Boolean?>(null) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sync), // Sync icon
                    contentDescription = stringResource(id = R.string.sync)
                )
            }
        }

        SearchBar(searchQuery) { newQuery ->
            searchQuery = newQuery
            viewModel.searchSongs(newQuery)
        }

        SongTable(songs, isWideScreen)

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(id = R.string.sync_title)) },
                text = { Text(stringResource(id = R.string.sync_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        viewModel.synchronizeDatabase { success ->
                            syncSuccess = success
                        }
                    }) {
                        Text(stringResource(id = R.string.yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(id = R.string.no))
                    }
                }
            )
        }

        syncSuccess?.let {
            val message = if (it) R.string.sync_success else R.string.sync_failed
            Text(text = stringResource(id = message), color = if (it) Color.Green else Color.Red)
        }
    }
}

@Composable
fun SearchBar(searchQuery: String, onSearchChange: (String) -> Unit) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        singleLine = true,
        label = { Text(stringResource(id = R.string.search)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        )
    )
}

@Composable
fun SongTable(songs: List<Song>, isWideScreen: Boolean) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Table Header (Outside LazyColumn)
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                text = stringResource(id = R.string.column_id),
                modifier = Modifier.width(50.dp), // Fixed width
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(id = R.string.column_title),
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
            if (isWideScreen) {
                Text(
                    text = stringResource(id = R.string.column_author),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(id = R.string.column_actions),
                modifier = Modifier.width(80.dp), // Fixed width
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider()

        // Scrollable List of Songs
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs) { song ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = song.id, modifier = Modifier.width(50.dp))
                    Text(text = song.title, modifier = Modifier.weight(1f))
                    if (isWideScreen) {
                        Text(text = song.author ?: "", modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.width(80.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.text),
                            contentDescription = stringResource(id = R.string.view_lyrics),
                            modifier = Modifier.size(24.dp).clickable { /* Text anzeigen */ }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(
                            painter = painterResource(id = R.drawable.chords),
                            contentDescription = stringResource(id = R.string.view_chords),
                            modifier = Modifier.size(24.dp).clickable { /* Bild anzeigen */ }
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
