package de.jeisfeld.songarchive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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

    val trimmedTitle = title.text.trim()
    val trimmedLyrics = lyrics.text.trim()
    val trimmedLyricsPagedText = lyricsPaged.text.trim()
    val trimmedLyricsPaged = trimmedLyricsPagedText.ifEmpty { null }

    val buttonContentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = buttonContentPadding
                ) {
                    Text(text = stringResource(id = R.string.cancel))
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        contentPadding = buttonContentPadding
                    ) {
                        Text(text = stringResource(id = R.string.delete))
                    }
                }
                TextButton(
                    onClick = {
                        onConfirm(trimmedTitle, trimmedLyrics, trimmedLyricsPaged)
                    },
                    enabled = trimmedTitle.isNotEmpty() && trimmedLyrics.isNotEmpty(),
                    contentPadding = buttonContentPadding
                ) {
                    Text(text = stringResource(id = R.string.save))
                }
            }
        }
    )
}
