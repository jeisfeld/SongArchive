package de.jeisfeld.songarchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

    // Load songs from database
    val songs by viewModel.songs.collectAsState()

    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stringResource(id = R.string.app_name),
            fontSize = MaterialTheme.typography.headlineMedium.fontSize,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        SearchBar(searchQuery) { newQuery ->
            searchQuery = newQuery
            viewModel.searchSongs(newQuery) // Search in DB
        }

        SongTable(songs, isWideScreen)
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
    Column(modifier = Modifier.fillMaxWidth()) {
        songs.forEach { song ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(text = song.id, modifier = Modifier.weight(0.2f))
                Text(text = song.title, modifier = Modifier.weight(0.4f))
                if (isWideScreen) {
                    Text(text = song.author, modifier = Modifier.weight(0.3f))
                }
                Row(modifier = Modifier.weight(0.3f)) {
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
        }
    }
}
