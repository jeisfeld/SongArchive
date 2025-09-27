package de.jeisfeld.songarchive.db

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.sync.CheckUpdateResponse
import de.jeisfeld.songarchive.sync.RetrofitClient
import de.jeisfeld.songarchive.util.LocalTabUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.regex.Pattern
import java.util.zip.ZipInputStream

class SongViewModel(application: Application) : AndroidViewModel(application) {
    val TAG = "SongViewModel"
    private val songDao = AppDatabase.getDatabase(application).songDao()
    private val favoriteDao = AppDatabase.getDatabase(application).favoriteListDao()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val client = RetrofitClient.httpClient
    var searchQuery = mutableStateOf("")
    var initState = MutableLiveData<Int>(0)
    var checkUpdateResponse: CheckUpdateResponse? = null
    private var searchJob: Job? = null
    private var hasEmittedInitialSongs = false

    init {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongs()
        }
        searchSongs(searchQuery.value)
    }

    fun isDatabaseEmpty(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val count = songDao.getSongCount()
            callback(count == 0)
        }
    }

    fun getMeaningsForSong(id: String): ArrayList<Meaning> {
        return ArrayList(songDao.getMeaningsForSong(id))
    }

    fun searchSongs(input: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val searchArgs = buildSearchArguments(input)
            songDao.searchSongsForArgs(searchArgs).collectLatest { results ->
                _songs.value = results
                if (!hasEmittedInitialSongs) {
                    hasEmittedInitialSongs = true
                    initState.postValue(1)
                }
            }
        }
    }

    fun shuffleSongs() {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongsRandom()
        }
    }

    fun addLocalSong(
        title: String,
        lyrics: String,
        lyricsPaged: String?,
        localTabUri: String?,
        onResult: (Song) -> Unit = {}
    ) {
        viewModelScope.launch {
            val newSong = withContext(Dispatchers.IO) {
                val newId = generateNextLocalSongId()
                val song = buildLocalSong(newId, title, lyrics, lyricsPaged, localTabUri)
                songDao.insertSong(song)
                song
            }
            refreshSongsList()
            onResult(newSong)
        }
    }

    fun cloneSongToLocal(
        originalSong: Song,
        title: String,
        lyrics: String,
        lyricsPaged: String?,
        localTabUri: String?,
        onResult: (Song) -> Unit = {},
    ) {
        viewModelScope.launch {
            val clonedSong = withContext(Dispatchers.IO) {
                val newId = generateNextLocalSongId()
                val trimmedTitle = title.trim()
                val trimmedLyrics = lyrics.trim()
                val sanitizedLyricsPaged = sanitizeLyricsPaged(lyricsPaged)
                val sanitizedLocalTab = localTabUri?.takeIf { it.isNotBlank() }
                val finalTabFilename = sanitizedLocalTab?.let { LocalTabUtils.encodeLocalTab(it) } ?: originalSong.tabfilename

                val newSong = originalSong.copy(
                    id = newId,
                    title = trimmedTitle,
                    lyrics = trimmedLyrics,
                    lyricsShort = null,
                    lyricsPaged = sanitizedLyricsPaged,
                    tabfilename = finalTabFilename,
                    mp3filename = originalSong.mp3filename,
                    mp3filename2 = originalSong.mp3filename2,
                    author = originalSong.author,
                    keywords = originalSong.keywords,
                    title_normalized = normalizeForSearch(trimmedTitle),
                    lyrics_normalized = normalizeForSearch(trimmedLyrics),
                    author_normalized = normalizeForSearch(originalSong.author),
                    keywords_normalized = normalizeForSearch(originalSong.keywords)
                )
                songDao.insertSong(newSong)
                newSong
            }
            refreshSongsList()
            onResult(clonedSong)
        }
    }

    fun updateLocalSong(
        songId: String,
        title: String,
        lyrics: String,
        lyricsPaged: String?,
        localTabUri: String?,
        onResult: (Song) -> Unit = {}
    ) {
        if (!songId.startsWith("Y")) {
            return
        }
        viewModelScope.launch {
            val updatedSong = withContext(Dispatchers.IO) {
                val sanitizedSong = buildLocalSong(songId, title, lyrics, lyricsPaged, localTabUri)
                val existingSong = songDao.getSongById(songId)
                val preservedRemoteTab = existingSong?.tabfilename?.takeIf { tab -> !LocalTabUtils.isLocalTab(tab) }
                val mergedTabFilename = sanitizedSong.tabfilename ?: preservedRemoteTab
                val mergedSong = existingSong?.copy(
                    title = sanitizedSong.title,
                    lyrics = sanitizedSong.lyrics,
                    lyricsShort = sanitizedSong.lyricsShort,
                    lyricsPaged = sanitizedSong.lyricsPaged,
                    author = sanitizedSong.author,
                    keywords = sanitizedSong.keywords,
                    tabfilename = mergedTabFilename,
                    title_normalized = sanitizedSong.title_normalized,
                    lyrics_normalized = sanitizedSong.lyrics_normalized,
                    author_normalized = sanitizedSong.author_normalized,
                    keywords_normalized = sanitizedSong.keywords_normalized
                ) ?: sanitizedSong.copy(tabfilename = mergedTabFilename)
                songDao.insertSong(mergedSong)
                mergedSong
            }
            refreshSongsList()
            onResult(updatedSong)
        }
    }

    fun uploadLocalSongToServer(
        songId: String,
        title: String,
        lyrics: String,
        lyricsPaged: String?,
        localTabUri: String?,
    ) {
        if (!songId.startsWith("Y")) {
            return
        }
        viewModelScope.launch {
            val uploadSuccessful = withContext(Dispatchers.IO) {
                try {
                    val sanitizedSong = buildLocalSong(songId, title, lyrics, lyricsPaged, localTabUri)
                    val existingSong = songDao.getSongById(songId)
                    val finalLyricsShort = sanitizedSong.lyricsShort ?: existingSong?.lyricsShort?.trim()
                    val finalLyricsPaged = sanitizedSong.lyricsPaged ?: existingSong?.lyricsPaged?.trim()
                    val finalAuthor = existingSong?.author?.trim()
                    val finalKeywords = existingSong?.keywords?.trim()

                    val formBodyBuilder = FormBody.Builder()
                        .add("id", songId)
                        .add("title", sanitizedSong.title)
                        .add("lyrics", sanitizedSong.lyrics)

                    finalLyricsShort?.takeIf { it.isNotBlank() }?.let {
                        formBodyBuilder.add("lyrics_short", it)
                    }
                    finalLyricsPaged?.takeIf { it.isNotBlank() }?.let {
                        formBodyBuilder.add("lyrics_paged", it)
                    }
                    finalAuthor?.takeIf { it.isNotBlank() }?.let {
                        formBodyBuilder.add("author", it)
                    }
                    finalKeywords?.takeIf { it.isNotBlank() }?.let {
                        formBodyBuilder.add("keywords", it)
                    }

                    val request = Request.Builder()
                        .url("https://heilsame-lieder.de/admin/addsong_external.php")
                        .post(formBodyBuilder.build())
                        .build()

                    client.newCall(request).execute().use { response ->
                        response.isSuccessful
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload song", e)
                    false
                }
            }

            val appContext = getApplication<Application>()
            val messageResId = if (uploadSuccessful) {
                R.string.upload_song_success
            } else {
                R.string.upload_song_failed
            }
            Toast.makeText(appContext, appContext.getString(messageResId), Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteLocalSong(songId: String, onComplete: () -> Unit = {}) {
        if (!songId.startsWith("Y")) {
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                songDao.deleteSongById(songId)
            }
            refreshSongsList()
            onComplete()
        }
    }

    fun synchronizeDatabaseAndImages(recheckUpdate: Boolean, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Synchronize private songs only in server mode
                val queryUser = if (pluginVerified.value ?: false) "private" else null

                val existingFavorites = favoriteDao.getAllEntries()

                // Fetch all data in one API call
                val response = RetrofitClient.api.fetchAllData(user = queryUser)

                // Process Songs
                val fetchedSongs = response.songs.map {
                    Song(
                        id = it.id,
                        title = it.title.trim(),
                        lyrics = it.lyrics?.trim() ?: "",
                        lyricsShort = it.lyrics_short?.trim(),
                        lyricsPaged = it.lyrics_paged?.trim(),
                        author = it.author?.trim() ?: "",
                        keywords = it.keywords?.trim() ?: "",
                        tabfilename = it.tabfilename,
                        mp3filename = it.mp3filename,
                        mp3filename2 = it.mp3filename2,

                        // Normalize fields before storing in Room
                        title_normalized = " " + removeAccents(it.title).trim(),
                        lyrics_normalized = " " + removeAccents(it.lyrics ?: "").trim(),
                        author_normalized = " " + removeAccents(it.author ?: "").trim(),
                        keywords_normalized = " " + removeAccents(it.keywords ?: "").trim()
                    )
                }

                // Process Meanings
                val fetchedMeanings = response.meanings.map {
                    Meaning(
                        id = it.id,
                        title = it.title.trim(),
                        meaning = it.meaning.trim()
                    )
                }

                // Process Song-Meaning Relationships
                val fetchedSongMeanings = response.song_meanings.map {
                    SongMeaning(
                        songId = it.song_id,
                        meaningId = it.meaning_id
                    )
                }

                // Store in local Room database
                songDao.clearMeanings()
                songDao.clearSongs()
                songDao.insertSongs(fetchedSongs)
                refreshSongsList()

                songDao.insertMeanings(fetchedMeanings)
                songDao.insertSongMeanings(fetchedSongMeanings)

                val localSongIds = songDao.getLocalSongIds()
                val validSongIds = (fetchedSongs.map { it.id } + localSongIds).toSet()
                favoriteDao.insertSongs(existingFavorites.filter { validSongIds.contains(it.songId) })

                // Step 2: Download and Extract Images
                val success = downloadAndExtractZip(getApplication(), "https://heilsame-lieder.de/admin/download_chords.php")

                if (success) {
                    if (recheckUpdate) {
                        checkUpdateResponse = RetrofitClient.api.checkUpdate()
                    }
                    storeLastAppMetadata()
                }

                // Step 3: Notify UI
                onComplete(success)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to synchronize: ${e.message}")
                onComplete(false)
            }
        }
    }

    private suspend fun refreshSongsList() {
        val currentQuery = withContext(Dispatchers.Main) { searchQuery.value }
        val updatedSongs = if (currentQuery.isBlank()) {
            withContext(Dispatchers.IO) { songDao.getAllSongs() }
        } else {
            val searchArgs = buildSearchArguments(currentQuery)
            withContext(Dispatchers.IO) {
                songDao.searchSongsForArgs(searchArgs).first()
            }
        }
        _songs.value = updatedSongs
    }

    private data class SearchArguments(
        val query: String,
        val fullQuery: String,
        val words: List<String>,
        val spacedWords: List<String>
    )

    private fun buildSearchArguments(rawInput: String): SearchArguments {
        val normalizedQuery = removeAccents(rawInput)
        val words = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val wordList = (words.map { "%$it%" }.take(5) + List(5) { "%%" }).take(5)
        val spacedWordList = (words.map { "% $it%" }.take(5) + List(5) { "%%" }).take(5)
        return SearchArguments(
            query = "%$normalizedQuery%",
            fullQuery = "% $normalizedQuery%",
            words = wordList,
            spacedWords = spacedWordList
        )
    }

    private fun SongDao.searchSongsForArgs(args: SearchArguments) = searchSongs(
        args.query,
        args.fullQuery,
        args.words[0], args.spacedWords[0],
        args.words[1], args.spacedWords[1],
        args.words[2], args.spacedWords[2],
        args.words[3], args.spacedWords[3],
        args.words[4], args.spacedWords[4]
    )

    private suspend fun generateNextLocalSongId(): String {
        val existingIds = songDao.getLocalSongIds()
        val usedNumbers = existingIds.mapNotNull { id ->
            id.removePrefix("Y").toIntOrNull()
        }.toSet()
        var candidate = 1
        while (usedNumbers.contains(candidate)) {
            candidate++
        }
        return "Y%03d".format(candidate)
    }

    private fun buildLocalSong(
        id: String,
        title: String,
        lyrics: String,
        lyricsPaged: String?,
        localTabUri: String?
    ): Song {
        val trimmedTitle = title.trim()
        val trimmedLyrics = lyrics.trim()
        val sanitizedLyricsPaged = sanitizeLyricsPaged(lyricsPaged)
        val sanitizedLocalTab = localTabUri?.takeIf { it.isNotBlank() }
        return Song(
            id = id,
            title = trimmedTitle,
            lyrics = trimmedLyrics,
            lyricsShort = null,
            lyricsPaged = sanitizedLyricsPaged,
            author = "",
            keywords = "",
            tabfilename = LocalTabUtils.encodeLocalTab(sanitizedLocalTab),
            mp3filename = null,
            mp3filename2 = null,
            title_normalized = normalizeForSearch(trimmedTitle),
            lyrics_normalized = normalizeForSearch(trimmedLyrics),
            author_normalized = normalizeForSearch(""),
            keywords_normalized = normalizeForSearch("")
        )
    }

    private fun sanitizeLyricsPaged(input: String?): String? {
        return input?.replace("\r\n", "\n")?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeForSearch(value: String): String {
        return " " + removeAccents(value).trim()
    }

    fun removeAccents(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")

        return pattern.matcher(normalized)
            .replaceAll("") // Remove accents
            .replace("ß", "ss") // German sharp S
            .replace("´", "'") // Normalize accents to plain apostrophe
            .replace("`", "'")
            .replace("’", "'")
            .replace("\n", " ")
    }

    private fun downloadAndExtractZip(context: Context, url: String): Boolean {
        return try {
            val zipFile = File(context.filesDir, "chords.zip")
            val imagesDir = File(context.filesDir, "chords")

            // Step 1: Download ZIP
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body ?: return false

            FileOutputStream(zipFile).use { output ->
                body.byteStream().copyTo(output)
            }

            // Step 2: Delete old images before extraction
            if (imagesDir.exists()) imagesDir.deleteRecursively()
            imagesDir.mkdirs()

            // Step 3: Extract ZIP contents
            val zipInputStream = ZipInputStream(FileInputStream(zipFile))
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val file = File(imagesDir, entry.name)
                FileOutputStream(file).use { output ->
                    zipInputStream.copyTo(output)
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            // Step 4: Cleanup ZIP file after extraction
            zipFile.delete()

            true // Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download or extract ZIP: ${e.message}")
            false // Failure
        }
    }

    fun checkForSongUpdates(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                checkUpdateResponse = RetrofitClient.api.checkUpdate()
                Log.d(TAG, "Remote Metadata: " + checkUpdateResponse)

                val localMetadata =
                    AppDatabase.getDatabase(getApplication()).appMetadataDao().get()
                        ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0, language = "system", defaultNetworkConnection = 0)
                Log.d(TAG, "Local Metadata: " + localMetadata)

                val tabsChanged = checkUpdateResponse?.tab_count != null && checkUpdateResponse?.tab_count != localMetadata.numberOfTabs
                val sizeChanged = checkUpdateResponse?.chords_zip_size != null && checkUpdateResponse?.chords_zip_size != localMetadata.chordsZipSize

                onResult(tabsChanged || sizeChanged)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for update: ${e.message}")
                onResult(false)
            }
        }
    }

    fun storeLastAppMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            checkUpdateResponse?.tab_count?.let { tabCount ->
                checkUpdateResponse?.chords_zip_size?.let { zipSize ->
                    val dao = AppDatabase.getDatabase(getApplication()).appMetadataDao()
                    val existing =
                        dao.get() ?: AppMetadata(numberOfTabs = tabCount, chordsZipSize = zipSize, language = "system", defaultNetworkConnection = 0)
                    dao.insert(
                        existing.copy(numberOfTabs = tabCount, chordsZipSize = zipSize)
                    )
                }
            }
            checkUpdateResponse = null
        }
    }

    val pluginVerified = MutableLiveData<Boolean>(false)

    val pluginResponseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val token = intent.getStringExtra("verification_token")
            if (token == "PLUGIN_VERIFIED_12345") {
                pluginVerified.postValue(true)
            }
        }
    }

    fun sendPluginVerificationBroadcast(context: Context) {
        val intent = Intent("de.jeisfeld.songarchivexplugin.ACTION_VERIFY")
        intent.setComponent(
            ComponentName(
                "de.jeisfeld.songarchivexplugin",
                "de.jeisfeld.songarchivexplugin.PluginReceiver"
            )
        )
        context.sendBroadcast(intent)

        // Secondary mechanism via content provider - seems to work better on OEM devices
        try {
            val bundle = context.contentResolver.call(
                "content://de.jeisfeld.songarchivexplugin.provider".toUri(),
                "verify", // method name
                null,     // optional arg
                null      // optional extras
            )
            val status = bundle?.getString("status")
            if (status == "PLUGIN_VERIFIED_12345") {
                pluginVerified.postValue(true)
            }
        } catch (_: Exception) {
            // Ignore
        }

    }
}
