package de.jeisfeld.songarchive.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun openDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun searchRanksTitleBeforeLyricsAndAllowsTermsAcrossFields() = runBlocking {
        val songs = database.songDao()
        songs.insertSongs(
            listOf(
                song("0012", "Morning river"),
                song("0011", "Water song", lyrics = "Morning river"),
                song("0010", "Morning light", keywords = "river"),
                song("0013", "Morning sun")
            )
        )

        val results = songs.searchSongs(
            "%Morning river%", "% Morning river%",
            "%Morning%", "% Morning%", "%river%", "% river%",
            "%%", "%%", "%%", "%%", "%%", "%%"
        ).first()

        assertEquals(listOf("0012", "0011", "0010"), results.map { it.id })
    }

    @Test
    fun browsePrioritizesZeroPrefixedIdsInDescendingOrder() = runBlocking {
        val songs = database.songDao()
        songs.insertSongs(listOf(song("Y001"), song("0010"), song("0100"), song("1000")))

        assertEquals(listOf("0100", "0010", "1000", "Y001"), songs.getAllSongs().map { it.id })
    }

    @Test
    fun clearingDownloadedSongsRetainsLocalSongsAndTheirFavoriteEntries() = runBlocking {
        val songs = database.songDao()
        val favorites = database.favoriteListDao()
        val local = song("Y001", "Local song")
        songs.insertSongs(listOf(song("0011"), local))
        val listId = favorites.insert(FavoriteList(name = "Practice")).toInt()
        val localEntry = FavoriteListSong(listId, local.id, position = 1, customTitle = "Warm-up")
        favorites.insertSongs(listOf(FavoriteListSong(listId, "0011"), localEntry))

        songs.clearSongs()

        assertNull(songs.getSongById("0011"))
        assertEquals(listOf(local), songs.getLocalSongs())
        assertEquals(listOf(FavoriteListSongWithSong(localEntry, local)), favorites.getSongEntries(listId))
    }

    @Test
    fun favoriteOrderAndCustomTitleAreSpecificToEachList() = runBlocking {
        val favorites = database.favoriteListDao()
        database.songDao().insertSongs(listOf(song("0011", "Original title"), song("0012")))
        val firstList = favorites.insert(FavoriteList(name = "Rehearsal")).toInt()
        val otherList = favorites.insert(FavoriteList(name = "Concert")).toInt()
        favorites.insertSongs(
            listOf(
                FavoriteListSong(firstList, "0011", position = 0),
                FavoriteListSong(firstList, "0012", position = 1),
                FavoriteListSong(otherList, "0011", customTitle = "Concert opener")
            )
        )

        favorites.updatePosition(firstList, "0011", 2)
        favorites.updateCustomTitle(firstList, "0011", "Rehearsal closer")

        val entries = favorites.getSongEntries(firstList)
        assertEquals(listOf("0012", "0011"), entries.map { it.song.id })
        assertEquals("Rehearsal closer", entries.last().entry.customTitle)
        assertEquals("Original title", entries.last().song.title)
        assertEquals("Concert opener", favorites.getSongEntries(otherList).single().entry.customTitle)
    }

    private fun song(id: String, title: String = "Fixture song", lyrics: String = "", keywords: String = "") =
        Song(
            id = id,
            title = title,
            lyrics = lyrics,
            lyricsShort = null,
            lyricsPaged = null,
            author = "",
            keywords = keywords,
            chordmarkup = null,
            tabfilename = null,
            mp3filename = null,
            mp3filename2 = null,
            title_normalized = " $title",
            lyrics_normalized = " $lyrics",
            author_normalized = " ",
            keywords_normalized = " $keywords"
        )
}
