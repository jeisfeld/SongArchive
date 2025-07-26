package de.jeisfeld.songarchive.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.times
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.db.FavoriteList
import de.jeisfeld.songarchive.ui.theme.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToFavoriteListDialog(
    lists: List<FavoriteList>,
    onAdd: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(setOf<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.add_to_favorite_list)) },
        text = {
            Box(modifier = Modifier.heightIn(max = dimensionResource(id = R.dimen.icon_size_large) * 8)) {
                LazyColumn {
                    items(lists) { list ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = dimensionResource(id = R.dimen.spacing_small))
                        ) {
                            Checkbox(
                                checked = selected.contains(list.id),
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + list.id else selected - list.id
                                }
                            )
                            Text(text = list.name, color = AppColors.TextColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(selected.toList()); onDismiss() }) {
                Text(stringResource(id = R.string.add_to_lists))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        }
    )
}
