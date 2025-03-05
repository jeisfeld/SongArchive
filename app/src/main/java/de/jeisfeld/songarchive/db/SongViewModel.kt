package de.jeisfeld.songarchive.db

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.jeisfeld.songarchive.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val songDao = AppDatabase.getDatabase(application, viewModelScope).songDao()
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs
    private val client = OkHttpClient()

    init {
        loadAllSongs()
    }

    fun loadAllSongs() {
        viewModelScope.launch {
            _songs.value = songDao.getAllSongs()
        }
    }

    fun searchSongs(query: String) {
        viewModelScope.launch {
            _songs.value = songDao.searchSongs(query)
        }
    }

    fun synchronizeDatabaseAndImages(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Synchronize Database
                val fetchedSongs = RetrofitClient.api.fetchSongs()
                songDao.clearSongs()
                songDao.insertSongs(fetchedSongs)
                _songs.value = fetchedSongs

                // Step 2: Download and Extract Images
                val success = downloadAndExtractZip(getApplication(), "https://jeisfeld.de/songarchive/download_chords.php")

                // Step 3: Notify UI
                onComplete(success)
            } catch (e: Exception) {
                Log.e("SongArchive", "Failed to synchronize: ${e.message}", e)
                onComplete(false)
            }
        }
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
