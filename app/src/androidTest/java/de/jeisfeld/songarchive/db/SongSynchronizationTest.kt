package de.jeisfeld.songarchive.db

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jeisfeld.songarchive.sync.ApiService
import de.jeisfeld.songarchive.sync.CheckUpdateResponse
import de.jeisfeld.songarchive.sync.SongResponse
import de.jeisfeld.songarchive.sync.SyncResponse
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongSynchronizationTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var database: AppDatabase
    private lateinit var filesDir: File
    private lateinit var viewModel: SongViewModel

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        filesDir = Files.createTempDirectory(application.cacheDir.toPath(), "song-sync-test-").toFile()
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) viewModel.viewModelScope.cancel()
        database.close()
        filesDir.deleteRecursively()
    }

    @Test
    fun startupSyncRecordsRecreatedZipAndRefreshesNewChordRowsBeforeCompleting() = runBlocking {
        val archive = chordZip()
        val oldMetadata = AppMetadata(numberOfTabs = 1, chordsZipSize = 10, language = "de", defaultNetworkConnection = 5)
        database.appMetadataDao().insert(oldMetadata)
        database.songDao().insertSong(existingSong)
        val api = ArchiveApi(archive)
        viewModel = SongViewModel(application, database, api, filesDir)

        withTimeout(10_000) { viewModel.songs.first { it == listOf(existingSong) } }
        assertTrue(awaitResult(viewModel::checkForSongUpdates))
        assertTrue(awaitResult(viewModel::synchronizeDatabaseAndImages))

        val song = viewModel.songs.value.single()
        assertEquals("new.jpg", song.tabfilename)
        assertTrue(File(filesDir, "chords/${song.tabfilename}").isFile)
        assertEquals(1, viewModel.chordRefreshKey.value)
        assertEquals(oldMetadata.copy(chordsZipSize = archive.size.toLong()), database.appMetadataDao().get())
        assertFalse(awaitResult(viewModel::checkForSongUpdates))
    }

    @Test
    fun missingPostDownloadMetadataDoesNotBlockChordRefreshOrSavingTheDownloadedSize() = runBlocking {
        val archive = chordZip()
        val api = ArchiveApi(archive, failPostDownloadCheck = true)
        viewModel = SongViewModel(application, database, api, filesDir)

        assertTrue(awaitResult(viewModel::synchronizeDatabaseAndImages))

        assertEquals(archive.size.toLong(), database.appMetadataDao().get()?.chordsZipSize)
        assertEquals("new.jpg", viewModel.songs.value.single().tabfilename)
        assertEquals(1, viewModel.chordRefreshKey.value)
    }

    @Test
    fun invalidDownloadKeepsPreviousMetadataAndImages() = runBlocking {
        val oldMetadata = AppMetadata(numberOfTabs = 1, chordsZipSize = 10)
        database.appMetadataDao().insert(oldMetadata)
        database.songDao().insertSong(existingSong)
        val images = File(filesDir, "chords").apply { mkdir() }
        File(images, "old.jpg").writeText("old image")
        viewModel = SongViewModel(application, database, ArchiveApi("Server error".toByteArray()), filesDir)

        assertFalse(awaitResult(viewModel::synchronizeDatabaseAndImages))

        assertEquals(oldMetadata, database.appMetadataDao().get())
        assertEquals("old image", File(images, "old.jpg").readText())
        assertEquals(listOf(existingSong), database.songDao().getAllSongs())
        assertEquals(0, viewModel.chordRefreshKey.value)
    }

    private suspend fun awaitResult(action: ((Boolean) -> Unit) -> Unit): Boolean =
        withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                action { continuation.resume(it) }
            }
        }

    private val existingSong = Song(
        id = "0012",
        title = "Existing song",
        lyrics = "Lyrics",
        lyricsShort = null,
        lyricsPaged = null,
        author = "",
        keywords = "",
        chordmarkup = null,
        tabfilename = null,
        mp3filename = null,
        mp3filename2 = null,
        title_normalized = " Existing song",
        lyrics_normalized = " Lyrics",
        author_normalized = " ",
        keywords_normalized = " "
    )

    private fun chordZip(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("new.jpg"))
            zip.write("new chord sheet".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private class ArchiveApi(private val archive: ByteArray, private val failPostDownloadCheck: Boolean = false) : ApiService {
        private var downloaded = false
        private var checksAfterDownload = 0

        override suspend fun fetchAllData(user: String?) = SyncResponse(
            songs = listOf(
                SongResponse("0012", "New chord sheet", "Lyrics", null, null, null, null, null, "new.jpg", null, null)
            ),
            meanings = emptyList(),
            song_meanings = emptyList()
        )

        override suspend fun checkUpdate(): CheckUpdateResponse {
            if (!downloaded) return CheckUpdateResponse(1, null)
            if (failPostDownloadCheck) throw IOException("Metadata temporarily unavailable")
            val reportedSize = if (checksAfterDownload++ == 0) null else archive.size.toLong()
            return CheckUpdateResponse(1, reportedSize)
        }

        override suspend fun downloadChordArchive() = archive.toResponseBody().also { downloaded = true }
    }
}
