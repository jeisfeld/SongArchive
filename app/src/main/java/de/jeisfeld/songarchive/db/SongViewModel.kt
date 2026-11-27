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
import androidx.room.withTransaction
import de.jeisfeld.songarchive.R
import de.jeisfeld.songarchive.sync.ApiService
import de.jeisfeld.songarchive.sync.CheckUpdateResponse
import de.jeisfeld.songarchive.sync.RetrofitClient
import de.jeisfeld.songarchive.sync.hasArchiveUpdate
import de.jeisfeld.songarchive.sync.installChordArchive
import de.jeisfeld.songarchive.util.LocalTabUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.io.File
import java.text.Normalizer
import java.util.regex.Pattern

class SongViewModel internal constructor(
    application: Application,
    private val database: AppDatabase,
    private val archiveApi: ApiService,
    private val archiveFilesDir: File
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application, AppDatabase.getDatabase(application), RetrofitClient.api, application.filesDir
    )

    val TAG = "SongViewModel"
    private val songDao = database.songDao()
    private val favoriteDao = database.favoriteListDao()
    private val metadataDao = database.appMetadataDao()
    private val syncMutex = Mutex()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val _chordRefreshKey = MutableStateFlow(0)
    val chordRefreshKey: StateFlow<Int> = _chordRefreshKey
    private val client = RetrofitClient.httpClient
    var searchQuery = mutableStateOf("")
    var initState = MutableLiveData<Int>(0)
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
            if (input.isEmpty()) {
                _songs.value = songDao.getAllSongs()
                if (!hasEmittedInitialSongs) {
                    hasEmittedInitialSongs = true
                    initState.postValue(1)
                }
            } else {
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
    }

    fun shuffleSongs() {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongsRandom()
        }
    }

    fun refreshSongs() {
        viewModelScope.launch {
            refreshSongsList()
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
                    chordmarkup = originalSong.chordmarkup.takeIf { sanitizedLocalTab == null },
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
                    chordmarkup = existingSong.chordmarkup.takeIf { sanitizedSong.tabfilename == null },
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
                    val preservedRemoteTab = existingSong?.tabfilename?.takeIf { tab -> !LocalTabUtils.isLocalTab(tab) }
                    val mergedTabFilename = sanitizedSong.tabfilename ?: preservedRemoteTab
                    val finalLyricsShort = sanitizedSong.lyricsShort ?: existingSong?.lyricsShort
                    val finalLyricsPaged = sanitizedSong.lyricsPaged ?: existingSong?.lyricsPaged

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
                    mergedTabFilename?.takeIf { it.isNotBlank() && !LocalTabUtils.isLocalTab(it) }?.let {
                        formBodyBuilder.add("tabfilename", it)
                    }
                    existingSong?.mp3filename?.takeIf { it.isNotBlank() }?.let {
                        formBodyBuilder.add("mp3filename", it)
                    }
                    existingSong?.mp3filename2?.takeIf { it.isNotBlank() }?.let {
                        formBodyBuilder.add("mp3filename2", it)
                    }
                    existingSong?.author?.takeIf { it.isNotBlank() }?.let {
                        formBodyBuilder.add("author", it)
                    }
                    existingSong?.keywords?.takeIf { it.isNotBlank() }?.let {
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

    fun synchronizeDatabaseAndImages(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = try {
                syncMutex.withLock {
                    withContext(Dispatchers.IO) { synchronizeArchive() }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to synchronize", e)
                false
            }
            onComplete(success)
        }
    }

    private suspend fun synchronizeArchive() {
        // Synchronize private songs only in server mode
        val queryUser = if (pluginVerified.value ?: false) "private" else null

        // Fetch all data in one API call
        val response = archiveApi.fetchAllData(user = queryUser)

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
                chordmarkup = it.chordmarkup?.trim(),
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
        val fetchedSongIds = fetchedSongs.map { it.id }.toSet()
        val fetchedMeaningIds = fetchedMeanings.map { it.id }.toSet()
        val fetchedSongMeanings = response.song_meanings.map {
            SongMeaning(
                songId = it.song_id,
                meaningId = it.meaning_id
            )
        }.filter { it.songId in fetchedSongIds && it.meaningId in fetchedMeaningIds }

        val remote = response.chord_markup_revision?.let { readArchiveMetadata(queryUser) }
        val remoteZipSize = remote?.chords_zip_size
        val localMetadata = if (remoteZipSize != null) metadataDao.get() else null
        val imageFiles = fetchedSongs.mapNotNull {
            it.tabfilename?.takeIf { name -> name.isNotBlank() && !LocalTabUtils.isLocalTab(name) }
        }
        val needsArchive = when {
            remote == null -> true
            remoteZipSize == null -> remote.tab_count != 0 || imageFiles.isNotEmpty()
            remoteZipSize != localMetadata?.chordsZipSize -> true
            response.chord_markup_revision == localMetadata?.chordMarkupRevision -> true
            else -> imageFiles.any { !File(archiveFilesDir, "chords/$it").isFile }
        }
        val zipSize = if (needsArchive) {
            archiveApi.downloadChordArchive().use { installChordArchive(archiveFilesDir, it.byteStream()) }
        } else {
            null
        }
        try {
            val checkedMetadata = if (zipSize != null) readArchiveMetadata(queryUser) ?: remote else remote

            database.withTransaction {
                val existingFavorites = favoriteDao.getAllEntries()
                songDao.clearMeanings()
                songDao.clearSongs()
                songDao.insertSongs(fetchedSongs)
                songDao.insertMeanings(fetchedMeanings)
                songDao.insertSongMeanings(fetchedSongMeanings)

                val validSongIds = fetchedSongIds + songDao.getLocalSongIds()
                favoriteDao.insertSongs(existingFavorites.filter { it.songId in validSongIds })

                val existing = metadataDao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0)
                metadataDao.insert(
                    existing.copy(
                        numberOfTabs = checkedMetadata?.tab_count ?: existing.numberOfTabs,
                        chordsZipSize = zipSize ?: existing.chordsZipSize,
                        chordMarkupRevision = response.chord_markup_revision
                    )
                )
            }
            refreshSongsList()
        } finally {
            if (zipSize != null) _chordRefreshKey.update { it + 1 }
        }
    }

    private suspend fun readArchiveMetadata(queryUser: String?): CheckUpdateResponse? =
        try {
            archiveApi.checkUpdate(queryUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh the archive metadata", e)
            null
        }

    private suspend fun refreshSongsList() = withContext(Dispatchers.Main) {
        val currentQuery = searchQuery.value
        val updatedSongs = if (currentQuery.isBlank()) {
            songDao.getAllSongs()
        } else {
            songDao.searchSongsForArgs(buildSearchArguments(currentQuery)).first()
        }
        if (searchQuery.value == currentQuery) _songs.value = updatedSongs
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
            chordmarkup = null,
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

    fun checkForSongUpdates(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val needsUpdate = try {
                syncMutex.withLock {
                    val queryUser = if (pluginVerified.value == true) "private" else null
                    val remote = archiveApi.checkUpdate(queryUser)
                    val local = metadataDao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0)
                    remote.hasArchiveUpdate(local.numberOfTabs, local.chordsZipSize, local.chordMarkupRevision)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for update", e)
                false
            }
            onResult(needsUpdate)
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
