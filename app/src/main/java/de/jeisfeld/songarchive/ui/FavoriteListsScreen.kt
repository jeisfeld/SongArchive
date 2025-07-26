package de.jeisfeld.songarchive.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.content.Intent
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
import de.jeisfeld.songarchive.ui.theme.AppColors

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = dimensionResource(id = R.dimen.spacing_small))
                            .clickable {
                                val intent = android.content.Intent(context, FavoriteListSongsActivity::class.java).apply {
                                    putExtra("LIST_ID", list.id)
                                    putExtra("LIST_NAME", list.name)
                                }
                                context.startActivity(intent)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = list.name, color = AppColors.TextColor, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renameTarget = list }) {
                            Image(painter = painterResource(id = R.drawable.ic_edit), contentDescription = stringResource(id = R.string.rename_favorite_list))
                        }
                        IconButton(onClick = { deleteTarget = list }) {
                            Image(painter = painterResource(id = R.drawable.ic_delete), contentDescription = stringResource(id = R.string.delete_favorite_list))
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(id = R.string.add_favorite_list)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text(stringResource(id = R.string.list_name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.addList(name)
                    showAdd = false
                }) { Text(stringResource(id = R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(id = R.string.cancel)) } }
        )
    }

    renameTarget?.let { list ->
        var name by remember { mutableStateOf(list.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(id = R.string.rename_favorite_list)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.rename(list, name)
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
