package de.jeisfeld.songarchive.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.jeisfeld.songarchive.firebase.FirebaseCloudVisionClient
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.util.LocalTabUtils
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val allowTabSelection = remember(initialTabFilename) {
        initialTabFilename.isNullOrBlank() || LocalTabUtils.isLocalTab(initialTabFilename)
    }
    var selectedTabUri by remember(initialTabFilename) { mutableStateOf(initialLocalTabUri.takeIf { allowTabSelection }) }
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
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val cloudVisionClient = remember { FirebaseCloudVisionClient() }
    var isOcrInProgress by remember { mutableStateOf(false) }
    var ocrStatusResId by remember { mutableStateOf<Int?>(null) }

    fun startOcrIfNeeded(uriString: String) {
        if (lyrics.text.isNotBlank() || isOcrInProgress) {
            return
        }
        val uri = Uri.parse(uriString)
        coroutineScope.launch {
            isOcrInProgress = true
            ocrStatusResId = R.string.ocr_status_in_progress
            try {
                val recognizedText = withContext(Dispatchers.IO) {
                    val imageBytes = readImageBytes(context, uri)
                    cloudVisionClient.recognizeHandwrittenText(imageBytes)
                }
                if (lyrics.text.isBlank()) {
                    val filteredText = recognizedText?.let(::filterChordOnlyLines)?.trim().orEmpty()
                    ocrStatusResId = if (filteredText.isNotEmpty()) {
                        lyrics = TextFieldValue(filteredText)
                        R.string.ocr_status_success
                    } else {
                        R.string.ocr_status_no_text
                    }
                } else {
                    ocrStatusResId = null
                }
            } catch (e: FirebaseCloudVisionClient.MissingApiKeyException) {
                ocrStatusResId = R.string.ocr_status_missing_api_key
            } catch (e: Exception) {
                ocrStatusResId = R.string.ocr_status_failed
            } finally {
                isOcrInProgress = false
            }
        }
    }

    val sanitizedInitialLongLyrics = remember(initialLongLyrics) {
        initialLongLyrics?.replace("\r\n", "\n")?.trim() ?: ""
    }

    val scrollState = rememberScrollState()

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val capturedUri = pendingCameraUri
            if (success && capturedUri != null) {
                val uriString = capturedUri.toString()
                selectedTabUri = uriString
                selectedTabDisplayName =
                    LocalTabUtils.getDisplayName(context, uriString)
                        ?: capturedUri.lastPathSegment.orEmpty()
                ocrStatusResId = null
                startOcrIfNeeded(uriString)
            } else if (!success && capturedUri != null) {
                try {
                    context.contentResolver.delete(capturedUri, null, null)
                } catch (_: SecurityException) {
                    // Ignore if we cannot delete the placeholder entry
                }
            }
            pendingCameraUri = null
        }

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Ignore if we cannot persist the permission; best effort only
            }
            val uriString = uri.toString()
            selectedTabUri = uriString
            selectedTabDisplayName = LocalTabUtils.getDisplayName(context, uriString) ?: uri.lastPathSegment.orEmpty()
            ocrStatusResId = null
            startOcrIfNeeded(uriString)
        }
    }

    LaunchedEffect(initialLocalTabUri, allowTabSelection) {
        if (allowTabSelection && initialLocalTabUri != null && selectedTabDisplayName.isEmpty()) {
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
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
                if (allowTabSelection) {
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
                                    ocrStatusResId = null
                                },
                                contentPadding = buttonContentPadding
                            ) {
                                Text(text = stringResource(id = R.string.remove_tab_file))
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    val captureUri = createImageCaptureUri(context)
                                    if (captureUri != null) {
                                        pendingCameraUri = captureUri
                                        ocrStatusResId = null
                                        takePictureLauncher.launch(captureUri)
                                    } else {
                                        ocrStatusResId = R.string.ocr_status_failed
                                    }
                                },
                                contentPadding = buttonContentPadding
                            ) {
                                Text(text = stringResource(id = R.string.capture_tab_photo))
                            }
                        }
                        TextButton(
                            onClick = { openDocumentLauncher.launch(arrayOf("image/*")) },
                            contentPadding = buttonContentPadding
                        ) {
                            Text(text = stringResource(id = R.string.select_tab_file))
                        }
                    }
                    if (isOcrInProgress || ocrStatusResId != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                id = ocrStatusResId ?: R.string.ocr_status_in_progress
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                        val tabUriForSaving = if (allowTabSelection) selectedTabUri else null
                        onConfirm(trimmedTitle, trimmedLyrics, trimmedLyricsPaged, tabUriForSaving)
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

private fun readImageBytes(context: Context, uri: Uri): ByteArray {
    return context.contentResolver.openInputStream(uri)?.use { inputStream ->
        inputStream.readBytes()
    } ?: throw IllegalStateException("Unable to read image bytes for $uri")
}

private fun createImageCaptureUri(context: Context): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "SongArchive_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/SongArchive"
            )
        }
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
}

private fun filterChordOnlyLines(text: String): String {
    val filteredLines = text
        .lineSequence()
        .filterNot { looksLikeChordLine(it) }
        .toList()
    return filteredLines.joinToString(separator = "\n")
}

private fun looksLikeChordLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) {
        return false
    }
    val tokens = trimmed.split(Regex("\\s+"))
    var consideredTokens = 0
    var chordTokens = 0
    for (token in tokens) {
        val sanitized = sanitizeChordToken(token)
        if (sanitized.isEmpty()) {
            continue
        }
        consideredTokens++
        if (isChordToken(sanitized)) {
            chordTokens++
        } else {
            return false
        }
    }
    return consideredTokens > 0 && chordTokens == consideredTokens
}

private fun sanitizeChordToken(token: String): String {
    val trimmed = token.trim { it.isWhitespace() || it in CHORD_BOUNDARY_CHARS }
    if (trimmed.isEmpty()) {
        return ""
    }
    return buildString(trimmed.length) {
        for (character in trimmed) {
            if (character !in CHORD_IGNORED_CHARS) {
                append(character)
            }
        }
    }
}

private fun isChordToken(token: String): Boolean {
    val normalized = token.uppercase(Locale.ROOT)
    if (normalized.isEmpty()) {
        return false
    }
    val parts = normalized.split('/')
    if (parts.isEmpty() || parts.size > 2) {
        return false
    }
    val mainPart = parts[0]
    if (!isChordRoot(mainPart)) {
        return false
    }
    if (parts.size == 2) {
        val bassPart = parts[1]
        if (bassPart.isNotBlank() && !isChordRoot(bassPart)) {
            return false
        }
    }
    return true
}

private fun isChordRoot(part: String): Boolean {
    if (part.isEmpty()) {
        return false
    }
    var index = 0
    val firstCharacter = part[index]
    if (firstCharacter !in CHORD_ROOT_LETTERS) {
        return false
    }
    index++
    if (index < part.length) {
        when {
            part.startsWith("#", index) -> index += 1
            part.startsWith("B", index) -> index += 1
            part.startsWith("IS", index) -> index += 2
            part.startsWith("ES", index) -> index += 2
        }
    }
    if (index >= part.length) {
        return true
    }
    val suffix = part.substring(index)
    return suffix.all { character ->
        character.isDigit() || character in CHORD_SUFFIX_ALLOWED_CHARS
    }
}

private val CHORD_BOUNDARY_CHARS = setOf('(', ')', '[', ']', '{', '}', '"', '\'', '“', '”', '‘', '’', '<', '>', '«', '»')
private val CHORD_IGNORED_CHARS = setOf('.', ',', ';', ':', '!', '?', '…')
private val CHORD_ROOT_LETTERS = setOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H')
private val CHORD_SUFFIX_ALLOWED_CHARS = setOf('M', 'A', 'J', 'O', 'R', 'I', 'N', 'U', 'S', 'D', 'L', 'G', '#', 'B', '+', 'T')
