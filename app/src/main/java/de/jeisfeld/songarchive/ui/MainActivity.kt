package de.jeisfeld.songarchive.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import de.jeisfeld.songarchive.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[SongViewModel::class.java]
        setContent {
            AppTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: SongViewModel) {
    val songs by viewModel.songs.collectAsState()
    val isWideScreen = LocalConfiguration.current.screenWidthDp > 600
    var showDialog by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    // If DB is empty on startup, then synchronize data
    LaunchedEffect(Unit) {
        viewModel.isDatabaseEmpty { isEmpty ->
            if (isEmpty) {
                isSyncing = true
                viewModel.synchronizeDatabaseAndImages { success ->
                    isSyncing = false
                }
            }
        }
    }

    // Wrap everything in a Box to ensure the overlay appears above content
    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {
        Column(modifier = Modifier.padding(start = dimensionResource(id = R.dimen.spacing_medium),
            end = dimensionResource(id = R.dimen.spacing_medium),
            top = dimensionResource(id = R.dimen.spacing_heading_vertical))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f), // Expands to take available space
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground), // Replace with actual launcher icon
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .padding(end = dimensionResource(id = R.dimen.spacing_medium)) // Space between icon and text
                            .size(dimensionResource(id = R.dimen.icon_size_medium))
                    )

                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        color = AppColors.TextColor
                    )

                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground), // Replace with actual launcher icon
                        contentDescription = "App Icon",
                        modifier = Modifier
                            .padding(start = dimensionResource(id = R.dimen.spacing_medium)) // Space between text and icon
                            .size(dimensionResource(id = R.dimen.icon_size_medium))
                    )
                }

                // Sync Button on the Right
                IconButton(onClick = { showDialog = true }, modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_large))) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_sync),
                        contentDescription = stringResource(id = R.string.sync),
                        tint = AppColors.TextColor,
                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                    )
                }
            }

            SearchBar(viewModel)

            SongTable(viewModel, songs, isWideScreen)

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text(stringResource(id = R.string.sync_title)) },
                    text = { Text(stringResource(id = R.string.sync_message), fontSize = MaterialTheme.typography.bodyLarge.fontSize) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDialog = false
                            isSyncing = true // Show progress
                            viewModel.synchronizeDatabaseAndImages { success ->
                                isSyncing = false
                                viewModel.searchSongs(viewModel.searchQuery.value)
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
            Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.spacing_heading_vertical)))
            Text(text = stringResource(id = R.string.syncing), color = Color.White)
        }
    }
}

@Composable
fun SearchBar(viewModel: SongViewModel) {
    OutlinedTextField(
        value = viewModel.searchQuery.value,
        onValueChange = {
            viewModel.searchQuery.value = it
            viewModel.searchSongs(it)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = dimensionResource(id = R.dimen.spacing_medium), end = dimensionResource(id = R.dimen.spacing_medium)),
        singleLine = true,
        label = { Text(stringResource(id = R.string.search)) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.Transparent,
            focusedBorderColor = AppColors.TextColorLight,
            unfocusedBorderColor = AppColors.TextColorLight,
            cursorColor = AppColors.TextColor
        ),
        trailingIcon = {
            if (viewModel.searchQuery.value.isNotEmpty()) {
                IconButton(onClick = {
                    viewModel.searchQuery.value = ""
                    viewModel.searchSongs("")
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Clear",
                        tint = Color.Gray,
                        modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                    )
                }
            }
        }
    )
}

