package de.jeisfeld.songarchive.ui.favoritelists

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.animateItemPlacement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.consume
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.audio.isInternetAvailable
import de.jeisfeld.songarchive.db.FavoriteList
import de.jeisfeld.songarchive.db.FavoriteListSongWithSong
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.db.SongViewModel
import de.jeisfeld.songarchive.ui.SearchBar
import de.jeisfeld.songarchive.ui.SongTable
import de.jeisfeld.songarchive.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteListSongsScreen(
    viewModel: SongViewModel,
    favViewModel: FavoriteListViewModel,
    listId: Int,
    initialList: FavoriteList,
    initialEntries: List<FavoriteListSongWithSong>,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isWide = LocalConfiguration.current.screenWidthDp > 600
    val isConnected = isInternetAvailable(context)

    val lists by favViewModel.lists.collectAsState()
    var currentList by remember { mutableStateOf(initialList) }
    LaunchedEffect(lists) {
        lists.firstOrNull { it.id == listId }?.let { currentList = it }
    }

    val entries = remember {
        mutableStateListOf<FavoriteListSongWithSong>().apply {
            addAll(initialEntries.sortedBy { it.entry.position })
        }
    }

    val searchResults by viewModel.songs.collectAsState()
    val chordRefreshKey by viewModel.chordRefreshKey.collectAsState()
    val query = viewModel.searchQuery.value
    var addMode by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FavoriteListSongWithSong?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val favoriteIds = entries.map { it.entry.songId }.toSet()
    val otherResults = if (query.isNotBlank() || addMode) searchResults.filterNot { favoriteIds.contains(it.id) } else emptyList()
    val filteredEntries = if (query.isNotBlank()) {
        entries.filter {
            it.entry.customTitle?.contains(query, ignoreCase = true) == true ||
                it.song.title.contains(query, ignoreCase = true) ||
                it.song.id.contains(query, ignoreCase = true)
        }
    } else entries

    val inListResults = if (query.isNotBlank()) searchResults.filter { favoriteIds.contains(it.id) } else entries.map { it.song }
    val combinedResults = inListResults + otherResults

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(currentList.name) },
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
                    SearchBar(viewModel, onShuffle = {
                        if (!currentList.isSorted) {
                            val shuffled = entries.shuffled()
                            entries.clear(); entries.addAll(shuffled)
                            favViewModel.updatePositions(listId, entries.map { it.entry.songId })
                        }
                    })
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
            if (currentList.isSorted) {
                if (query.isNotBlank()) {
                    Text(
                        text = stringResource(id = R.string.favorite_clear_search_reorder),
                        color = AppColors.TextColor,
                        modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.spacing_medium))
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.favorite_sorted_notice),
                        color = AppColors.TextColor,
                        modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.spacing_medium))
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(filteredEntries, key = { _, item -> item.entry.songId }) { index, entry ->
                        val iconSize = dimensionResource(id = R.dimen.icon_size_small)
                        var offsetY by remember { mutableStateOf(0f) }
                        var rowHeight by remember { mutableStateOf(1) }
                        val isDragging = draggedId == entry.entry.songId

                        fun finalizeDrag(updateDb: Boolean) {
                            offsetY = 0f
                            draggedId = null
                            if (updateDb) {
                                entries.forEachIndexed { idx, item ->
                                    entries[idx] = item.copy(entry = item.entry.copy(position = idx))
                                }
                                favViewModel.updatePositions(listId, entries.map { it.entry.songId })
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(0, offsetY.roundToInt()) }
                                .zIndex(if (isDragging) 1f else 0f)
                                .pointerInput(query, entries.size) {
                                    if (query.isBlank()) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedId = entry.entry.songId
                                                offsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (draggedId != entry.entry.songId) return@detectDragGesturesAfterLongPress
                                                change.consume()
                                                offsetY += dragAmount.y
                                                val itemHeight = rowHeight.takeIf { it != 0 } ?: 1
                                                val currentIndex = entries.indexOfFirst { it.entry.songId == entry.entry.songId }
                                                if (currentIndex == -1) return@detectDragGesturesAfterLongPress
                                                val targetIndex = (currentIndex + (offsetY / itemHeight).roundToInt()).coerceIn(0, entries.lastIndex)
                                                if (targetIndex != currentIndex) {
                                                    entries.move(currentIndex, targetIndex)
                                                    offsetY -= (targetIndex - currentIndex) * itemHeight
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggedId == entry.entry.songId) {
                                                    finalizeDrag(updateDb = true)
                                                }
                                            },
                                            onDragCancel = {
                                                if (draggedId == entry.entry.songId) {
                                                    finalizeDrag(updateDb = false)
                                                }
                                            }
                                        )
                                    }
                                }
                                .padding(horizontal = dimensionResource(id = R.dimen.spacing_medium), vertical = 2.dp)
                                .onGloballyPositioned { rowHeight = it.size.height }
                                .animateItemPlacement(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.spacing_small))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_list),
                                contentDescription = stringResource(id = R.string.drag_to_reorder),
                                tint = AppColors.TextColor
                            )
                            Text(
                                text = (entries.indexOfFirst { it.entry.songId == entry.entry.songId } + 1).toString(),
                                modifier = Modifier.width(dimensionResource(id = R.dimen.width_id)),
                                color = AppColors.TextColor
                            )
                            if (editingId == entry.entry.songId) {
                                var titleDraft by remember(entry.entry.songId) {
                                    mutableStateOf(entry.entry.customTitle ?: entry.song.title)
                                }
                                OutlinedTextField(
                                    value = titleDraft,
                                    onValueChange = { titleDraft = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(stringResource(id = R.string.favorite_custom_title_hint)) },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        editingId = null
                                        val sanitized = titleDraft.trim().ifBlank { null }
                                        val idx = entries.indexOfFirst { it.entry.songId == entry.entry.songId }
                                        if (idx >= 0) {
                                            entries[idx] = entries[idx].copy(entry = entries[idx].entry.copy(customTitle = sanitized))
                                        }
                                        favViewModel.updateCustomTitle(listId, entry.entry.songId, sanitized)
                                    })
                                )
                            } else {
                                Text(
                                    text = entry.entry.customTitle ?: entry.song.title,
                                    modifier = Modifier.weight(1f).clickable { editingId = entry.entry.songId },
                                    color = AppColors.TextColor
                                )
                            }
                            IconButton(onClick = { deleteTarget = entry }, modifier = Modifier.width(iconSize)) {
                                Image(painter = painterResource(id = R.drawable.ic_delete), contentDescription = stringResource(id = R.string.remove_from_list))
                            }
                        }
                    }
                }

                if (addMode || query.isNotBlank()) {
                    SongTable(
                        viewModel = viewModel,
                        songs = otherResults,
                        isWideScreen = isWide,
                        isConnected = isConnected,
                        chordRefreshKey = chordRefreshKey,
                        onAddToList = { song ->
                            scope.launch {
                                favViewModel.addSongToList(listId, song.id)
                                val updated = withContext(Dispatchers.IO) { favViewModel.getSongEntries(listId) }
                                entries.clear(); entries.addAll(updated.sortedBy { it.entry.position })
                            }
                        },
                        addableIds = otherResults.map { it.id }.toSet()
                    )
                }
            } else {
                SongTable(
                    viewModel = viewModel,
                    songs = combinedResults,
                    isWideScreen = isWide,
                    isConnected = isConnected,
                    chordRefreshKey = chordRefreshKey,
                    onRemoveFromList = { song ->
                        deleteTarget = entries.firstOrNull { it.entry.songId == song.id }
                    },
                    removableIds = favoriteIds,
                    onAddToList = if (addMode) { song ->
                        scope.launch {
                            favViewModel.addSongToList(listId, song.id)
                            val updated = withContext(Dispatchers.IO) { favViewModel.getSongEntries(listId) }
                            entries.clear(); entries.addAll(updated.sortedBy { it.entry.position })
                        }
                    } else null,
                    addableIds = if (addMode) otherResults.map { it.id }.toSet() else emptySet()
                )
            }

            deleteTarget?.let { target ->
                AlertDialog(
                    onDismissRequest = { deleteTarget = null },
                    title = { Text(stringResource(id = R.string.remove_from_list)) },
                    text = { Text(stringResource(id = R.string.confirm_remove_from_list, target.entry.customTitle ?: target.song.title)) },
                    confirmButton = {
                        TextButton(onClick = {
                            favViewModel.removeSongFromList(listId, target.entry.songId)
                            entries.removeAll { it.entry.songId == target.entry.songId }
                            entries.forEachIndexed { idx, item ->
                                entries[idx] = item.copy(entry = item.entry.copy(position = idx))
                            }
                            favViewModel.updatePositions(listId, entries.map { it.entry.songId })
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

private fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return
    val element = removeAt(fromIndex)
    add(toIndex, element)
}
