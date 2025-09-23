package de.jeisfeld.songarchive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import de.jeisfeld.songarchive.R

@Composable
fun LocalSongDialog(
    isEditing: Boolean,
    initialTitle: String = "",
    initialLyrics: String = "",
    initialLyricsPaged: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(TextFieldValue(initialTitle)) }
    var lyrics by remember { mutableStateOf(TextFieldValue(initialLyrics)) }
    var lyricsPaged by remember { mutableStateOf(TextFieldValue(initialLyricsPaged)) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(text = stringResource(id = R.string.delete_song)) },
            text = { Text(text = stringResource(id = R.string.confirm_delete_song)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete?.invoke()
                    }
                ) {
                    Text(text = stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = if (isEditing) R.string.edit_song else R.string.add_song))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(id = R.string.song_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text(text = stringResource(id = R.string.song_lyrics)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    minLines = 4,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = lyricsPaged,
                    onValueChange = { lyricsPaged = it },
                    label = { Text(text = stringResource(id = R.string.song_lyrics_paged)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            val trimmedTitle = title.text.trim()
            val trimmedLyrics = lyrics.text.trim()
            val trimmedLyricsPaged = lyricsPaged.text.trim().ifEmpty { null }
            TextButton(
                onClick = {
                    onConfirm(trimmedTitle, trimmedLyrics, trimmedLyricsPaged)
                },
                enabled = trimmedTitle.isNotEmpty() && trimmedLyrics.isNotEmpty()
            ) {
                Text(text = stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            Column {
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirmation = true }) {
                        Text(text = stringResource(id = R.string.delete))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        }
    )
}
