package de.jeisfeld.songarchive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.R
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
    val songs by viewModel.songs.collectAsState()
    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600
    var showDialog by remember { mutableStateOf(false) }
    var syncSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(syncSuccess) {
        if (syncSuccess != null) {
            isSyncing = false // Hide progress when sync completes
        }
    }

    // Wrap everything in a Box to ensure the overlay appears above content
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(0.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.app_name),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                )
                IconButton(onClick = { showDialog = true },
                    modifier = Modifier.align(Alignment.CenterVertically),
                    ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sync),
                        contentDescription = stringResource(id = R.string.sync)
                    )
                }
            }

            SearchBar(viewModel)

            SongTable(viewModel, songs, isWideScreen)

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text(stringResource(id = R.string.sync_title)) },
                    text = { Text(stringResource(id = R.string.sync_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialog = false
                            isSyncing = true // Show progress
                            syncSuccess = null // Reset sync status
                            viewModel.synchronizeDatabaseAndImages { success ->
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

        // Ensure overlay appears when isSyncing is true
        if (isSyncing) {
            ProgressOverlay()
        }
    }
}

@Composable
fun ProgressOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)), // Semi-transparent background
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White) // Spinning loader
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(id = R.string.syncing), color = Color.White)
        }
    }
}

@Composable
fun SearchBar(viewModel: SongViewModel) {
    var searchQuery by remember { mutableStateOf("") }

    OutlinedTextField(
        value = searchQuery,
        onValueChange = {
            searchQuery = it
            viewModel.searchSongs(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        singleLine = true,
        label = { Text(stringResource(id = R.string.search)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = {
                    searchQuery = "" // Clear text
                    viewModel.searchSongs("") // Reset search results
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = Color.Gray
                    )
                }
            }
        }
    )
}

