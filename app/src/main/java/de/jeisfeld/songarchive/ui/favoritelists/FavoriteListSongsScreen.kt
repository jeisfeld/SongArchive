package de.jeisfeld.songarchive.ui.favoritelists

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.audio.isInternetAvailable
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.db.Song
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.SearchBar
import de.jeisfeld.songarchive.ui.SongTable
import de.jeisfeld.songarchive.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteListSongsScreen(
    viewModel: SongViewModel,
    favViewModel: FavoriteListViewModel,
    listId: Int,
    listName: String,
    initialSongs: List<Song>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isWide = LocalConfiguration.current.screenWidthDp > 600
    val isConnected = isInternetAvailable(context)

    var songsInList by remember { mutableStateOf(initialSongs) }
    val searchResults by viewModel.songs.collectAsState()
    val query = viewModel.searchQuery.value
    var addMode by remember { mutableStateOf(false) }
    val favoriteIds = songsInList.map { it.id }.toSet()

    val inListResults = if (query.isNotBlank()) searchResults.filter { favoriteIds.contains(it.id) } else songsInList
    val otherResults = if (query.isNotBlank() || addMode) searchResults.filterNot { favoriteIds.contains(it.id) } else emptyList()
    val combinedResults = inListResults + otherResults

    var deleteTarget by remember { mutableStateOf<Song?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(listName) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.Background,
                        titleContentColor = AppColors.TextColor
                    ),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = stringResource(id = R.string.cancel),
                                modifier = Modifier.padding(dimensionResource(id = R.dimen.spacing_small))
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { addMode = !addMode },
                            colors = if (addMode) {
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = AppColors.TextColor,
                                    contentColor = AppColors.Background
                                )
                            } else IconButtonDefaults.iconButtonColors()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = stringResource(id = R.string.add_song_to_list),
                                tint = if (addMode) AppColors.Background else AppColors.TextColor
                            )
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensionResource(id = R.dimen.spacing_medium))
                        .offset(y = -12.dp)
                ) {
                    SearchBar(viewModel, onShuffle = { songsInList = songsInList.shuffled() })
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
        ) {
            SongTable(
                viewModel,
                combinedResults,
                isWide,
                isConnected,
                onRemoveFromList = { song ->
                    deleteTarget = song
                },
                removableIds = favoriteIds,
                onAddToList = if (addMode) { song ->
                    favViewModel.addSongToList(listId, song.id)
                    songsInList = songsInList + song
                } else null,
                addableIds = if (addMode) otherResults.map { it.id }.toSet() else emptySet()
            )
            deleteTarget?.let { song ->
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = { Text(stringResource(id = R.string.remove_from_list)) },
                    text = { Text(stringResource(id = R.string.confirm_remove_from_list, song.title)) },
                    confirmButton = {
                        TextButton(onClick = {
                            favViewModel.removeSongFromList(listId, song.id)
                            songsInList = songsInList.filterNot { it.id == song.id }
                            deleteTarget = null
                        }) {
                            Text(stringResource(id = R.string.ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteTarget = null }) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}
