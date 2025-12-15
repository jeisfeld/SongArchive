package de.jeisfeld.songarchive.ui.favoritelists

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.FavoriteList
import de.jeisfeld.songarchive.db.FavoriteListViewModel
import de.jeisfeld.songarchive.network.PeerConnectionAction
import de.jeisfeld.songarchive.network.PeerConnectionMode
import de.jeisfeld.songarchive.network.PeerConnectionService
import de.jeisfeld.songarchive.network.PeerConnectionViewModel
import de.jeisfeld.songarchive.ui.theme.AppColors
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteListsScreen(viewModel: FavoriteListViewModel, onClose: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FavoriteList?>(null) }
    var deleteTarget by remember { mutableStateOf<FavoriteList?>(null) }

    val lists by viewModel.lists.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.favorite_lists)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextColor
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close",
                            modifier = Modifier.padding(dimensionResource(id = R.dimen.spacing_small))
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_add),
                            contentDescription = stringResource(id = R.string.add_favorite_list)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = dimensionResource(id = R.dimen.spacing_medium))
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(lists) { list ->
                    val scope = rememberCoroutineScope()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(id = R.dimen.spacing_vertical_medium))
                            .clickable {
                                val intent = Intent(context, FavoriteListSongsActivity::class.java).apply {
                                    putExtra("LIST_ID", list.id)
                                    putExtra("LIST_NAME", list.name)
                                }
                                context.startActivity(intent)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(
                            dimensionResource(id = R.dimen.spacing_medium),
                            Alignment.End
                        )
                    ) {
                        Text(text = list.name, color = AppColors.TextColor, modifier = Modifier.weight(1f))
                        if (PeerConnectionViewModel.peerConnectionMode == PeerConnectionMode.SERVER &&
                            PeerConnectionViewModel.connectedDevices > 0) {
                            IconButton(
                                onClick = {
                                scope.launch {
                                    val entries = withContext(Dispatchers.IO) { viewModel.getSongEntries(list.id) }
                                    val ids = entries.joinToString(",") { it.entry.songId }
                                    val payload = org.json.JSONArray().apply {
                                        entries.sortedBy { it.entry.position }.forEach { entry ->
                                            put(org.json.JSONObject().apply {
                                                put("songId", entry.entry.songId)
                                                put("position", entry.entry.position)
                                                put("customTitle", entry.entry.customTitle)
                                            })
                                        }
                                    }.toString()
                                    val intent = Intent(context, PeerConnectionService::class.java).apply {
                                        setAction(PeerConnectionAction.SHARE_FAVORITE_LIST.toString())
                                        putExtra("ACTION", PeerConnectionAction.SHARE_FAVORITE_LIST)
                                        putExtra("LIST_NAME", list.name)
                                        putExtra("SONG_IDS", ids)
                                        putExtra("LIST_SORTED", list.isSorted)
                                        putExtra("LIST_ENTRIES", payload)
                                    }
                                    context.startService(intent)
                                }
                            },
                                modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                            ) {
                                Image(painter = painterResource(id = R.drawable.ic_send), contentDescription = stringResource(id = R.string.share_favorite_list))
                            }
                        }
                        IconButton(
                            onClick = { renameTarget = list },
                            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                        ) {
                            Image(painter = painterResource(id = R.drawable.ic_edit), contentDescription = stringResource(id = R.string.rename_favorite_list))
                        }
                        IconButton(
                            onClick = { deleteTarget = list },
                            modifier = Modifier.size(dimensionResource(id = R.dimen.icon_size_small))
                        ) {
                            Image(painter = painterResource(id = R.drawable.ic_delete), contentDescription = stringResource(id = R.string.delete_favorite_list))
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var sorted by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(id = R.string.add_favorite_list)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.spacing_small))) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text(stringResource(id = R.string.list_name)) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sorted, onCheckedChange = { sorted = it })
                        Column {
                            Text(text = stringResource(id = R.string.sorted_list), color = AppColors.TextColor)
                            Text(text = stringResource(id = R.string.favorite_sorted_hint), color = AppColors.TextColor)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.addList(name.trim(), sorted)
                    showAdd = false
                }) { Text(stringResource(id = R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(id = R.string.cancel)) } }
        )
    }

    renameTarget?.let { list ->
        var name by remember { mutableStateOf(list.name) }
        var sorted by remember { mutableStateOf(list.isSorted) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(id = R.string.rename_favorite_list)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.spacing_small))) {
                    OutlinedTextField(value = name, onValueChange = { name = it })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = sorted, onCheckedChange = { sorted = it })
                        Column {
                            Text(text = stringResource(id = R.string.sorted_list), color = AppColors.TextColor)
                            Text(text = stringResource(id = R.string.favorite_sorted_hint), color = AppColors.TextColor)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.rename(list, name.trim(), sorted)
                    renameTarget = null
                }) { Text(stringResource(id = R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(id = R.string.cancel)) } }
        )
    }

    deleteTarget?.let { list ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(id = R.string.delete_favorite_list)) },
            text = { Text(stringResource(id = R.string.confirm_delete_list, list.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(list)
                    deleteTarget = null
                }) { Text(stringResource(id = R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(id = R.string.cancel)) } }
        )
    }
}
