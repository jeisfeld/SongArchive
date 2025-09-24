package de.jeisfeld.songarchive.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.util.LocalTabUtils

@Composable
fun LocalSongDialog(
    isEditing: Boolean,
    initialTitle: String = "",
    initialLyrics: String = "",
    initialLyricsPaged: String = "",
    initialTabFilename: String? = null,
    initialLongLyrics: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember { mutableStateOf(TextFieldValue(initialTitle)) }
    var lyrics by remember { mutableStateOf(TextFieldValue(initialLyrics)) }
    var lyricsPaged by remember { mutableStateOf(TextFieldValue(initialLyricsPaged)) }
    val context = LocalContext.current
    val initialLocalTabUri = remember(initialTabFilename) { LocalTabUtils.decodeLocalTab(initialTabFilename) }
    var selectedTabUri by remember(initialTabFilename) { mutableStateOf(initialLocalTabUri) }
    var selectedTabDisplayName by remember(initialTabFilename) {
        mutableStateOf(
            initialLocalTabUri?.let { LocalTabUtils.getDisplayName(context, it) }
                ?: initialTabFilename?.takeIf { it.isNotBlank() }?.let { filename ->
                    val afterSlash = filename.substringAfterLast('/', filename)
                    val afterBackslash = afterSlash.substringAfterLast('\\', afterSlash)
                    afterBackslash.ifBlank { filename }
                }
                ?: ""
        )
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val sanitizedInitialLongLyrics = remember(initialLongLyrics) {
        initialLongLyrics?.replace("\r\n", "\n")?.trim() ?: ""
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Ignore if we cannot persist the permission; best effort only
            }
            selectedTabUri = uri.toString()
            selectedTabDisplayName = LocalTabUtils.getDisplayName(context, uri.toString()) ?: uri.lastPathSegment.orEmpty()
        }
    }

    LaunchedEffect(initialLocalTabUri) {
        if (initialLocalTabUri != null && selectedTabDisplayName.isEmpty()) {
            selectedTabDisplayName = LocalTabUtils.getDisplayName(context, initialLocalTabUri) ?: ""
        }
    }

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
                if (sanitizedInitialLongLyrics.isNotEmpty()) {
                    OutlinedTextField(
                        value = sanitizedInitialLongLyrics,
                        onValueChange = {},
                        label = { Text(text = stringResource(id = R.string.song_lyrics_long)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        minLines = 4,
                        readOnly = true,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = lyricsPaged,
                    onValueChange = { lyricsPaged = it },
                    label = { Text(text = stringResource(id = R.string.song_lyrics_paged)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    minLines = 3,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = selectedTabDisplayName,
                    onValueChange = {},
                    label = { Text(text = stringResource(id = R.string.song_tab_file)) },
                    placeholder = { Text(text = stringResource(id = R.string.no_tab_file_selected)) },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    singleLine = true,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedTabUri != null) {
                        TextButton(
                            onClick = {
                                selectedTabUri = null
                                selectedTabDisplayName = ""
                            },
                            contentPadding = buttonContentPadding
                        ) {
                            Text(text = stringResource(id = R.string.remove_tab_file))
                        }
                    }
                    TextButton(
                        onClick = { openDocumentLauncher.launch(arrayOf("image/*")) },
                        contentPadding = buttonContentPadding
                    ) {
                        Text(text = stringResource(id = R.string.select_tab_file))
                    }
                }
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
                        onConfirm(trimmedTitle, trimmedLyrics, trimmedLyricsPaged, selectedTabUri)
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
