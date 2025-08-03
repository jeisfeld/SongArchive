package de.jeisfeld.songarchive.db

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.jeisfeld.songarchive.sync.CheckUpdateResponse
import de.jeisfeld.songarchive.sync.RetrofitClient
import de.jeisfeld.songarchive.db.FavoriteListSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
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
    private val client = OkHttpClient()
    var searchQuery = mutableStateOf("")
    var initState = MutableLiveData<Int>(0)
    var checkUpdateResponse: CheckUpdateResponse? = null

    init {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongs()
            initState.postValue(1)
        }
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
        viewModelScope.launch {
            val normalizedQuery = removeAccents(input) // Normalize accents & quotes
            val words = normalizedQuery.split(" ").filter { it.isNotBlank() }
            val wordList = words.map { "%$it%" }.take(5) + List(5) { "%%" } // Ensure 5 words are always passed
            val wordList2 = words.map { "% $it%" }.take(5) + List(5) { "%%" }

            songDao.searchSongs(
                "%$normalizedQuery%",
                "% $normalizedQuery%",
                wordList[0], wordList2[0], wordList[1], wordList2[1], wordList[2], wordList2[2], wordList[3], wordList2[3], wordList[4], wordList2[4]
            ).collectLatest { results ->
                _songs.value = results
            }
        }
    }

    fun shuffleSongs() {
        viewModelScope.launch {
            songDao.getSongsShuffled().collectLatest { results ->
                _songs.value = results
            }
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
                _songs.value = fetchedSongs

                songDao.insertMeanings(fetchedMeanings)
                songDao.insertSongMeanings(fetchedSongMeanings)

                val validSongIds = fetchedSongs.map { it.id }.toSet()
                favoriteDao.insertSongs(existingFavorites.filter { validSongIds.contains(it.songId) })

                // Step 2: Download and Extract Images
                val success = downloadAndExtractZip(getApplication(), "https://heilsame-lieder.de/download_chords.php")

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
        CoroutineScope(Dispatchers.IO).launch {
            checkUpdateResponse?.tab_count?.let { tabCount ->
                checkUpdateResponse?.chords_zip_size?.let { zipSize ->
                    val dao = AppDatabase.getDatabase(getApplication()).appMetadataDao()
                    val existing = dao.get() ?: AppMetadata(numberOfTabs = tabCount, chordsZipSize = zipSize, language = "system", defaultNetworkConnection = 0)
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
        }
        catch (_: Exception) {
            // Ignore
        }

    }
}
