package de.jeisfeld.songarchive.db

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import de.jeisfeld.songarchive.RetrofitClient
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
    private val songDao = AppDatabase.getDatabase(application, viewModelScope).songDao()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val client = OkHttpClient()

    var currentlyPlayingSong = mutableStateOf<Song?>(null)
    var isPlaying = mutableStateOf(false)
    var currentProgress = mutableStateOf(0L)
    var searchQuery = mutableStateOf("")
    var exoPlayer: ExoPlayer? = null

    init {
        loadAllSongs()
    }

    fun loadAllSongs() {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongs()
        }
    }

    fun isDatabaseEmpty(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val count = songDao.getSongCount()
            callback(count == 0)
        }
    }

    fun getExoPlayer(context: Context): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build()
        }
        return exoPlayer!!
    }

    fun releaseExoPlayer() {
        exoPlayer?.release()
        exoPlayer = null
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
    fun synchronizeDatabaseAndImages(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch all data in one API call
                val response = RetrofitClient.api.fetchAllData()

                // Process Songs
                val fetchedSongs = response.songs.map {
                    Song(
                        id = it.id,
                        title = it.title,
                        lyrics = it.lyrics ?: "",
                        author = it.author ?: "",
                        keywords = it.keywords ?: "",
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
                        title = it.title,
                        meaning = it.meaning
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

                // Step 2: Download and Extract Images
                val success = downloadAndExtractZip(getApplication(), "https://heilsame-lieder.de/download_chords.php")

                // Step 3: Notify UI
                onComplete(success)
            } catch (e: Exception) {
                Log.e("SyncError", "Failed to synchronize: ${e.message}")
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
            Log.e("ZipError", "Failed to download or extract ZIP: ${e.message}")
            false // Failure
        }
    }
}
