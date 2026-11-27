package de.jeisfeld.songarchive.sync

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncResponseTest {
    private val gson = Gson()

    @Test
    fun downloadKeepsStringIdsOptionalMediaAndMeaningLinks() {
        val response = gson.fromJson(
            """
            {
              "songs": [
                {
                  "id": "0012a", "title": "Morning River", "lyrics": "First line",
                  "lyrics_short": "Chorus", "lyrics_paged": "Page one|Page two",
                  "author": "Ada", "keywords": "water", "chordmarkup": "[Am]",
                  "tabfilename": "river.jpg", "mp3filename": "river.mp3",
                  "mp3filename2": "river-alternate.mp3"
                },
                {"id": "Y105", "title": "A song without media", "lyrics": null}
              ],
              "meanings": [{"id": "17", "title": "Context", "meaning": "A fictional translation"}],
              "song_meanings": [{"song_id": "0012a", "meaning_id": "17"}]
            }
            """.trimIndent(),
            SyncResponse::class.java
        )

        assertEquals(
            SongResponse(
                "0012a", "Morning River", "First line", "Chorus", "Page one|Page two",
                "Ada", "water", "[Am]", "river.jpg", "river.mp3", "river-alternate.mp3"
            ),
            response.songs.first()
        )
        assertEquals(
            SongResponse("Y105", "A song without media", null, null, null, null, null, null, null, null, null),
            response.songs.last()
        )
        assertEquals(listOf(MeaningResponse(17, "Context", "A fictional translation")), response.meanings)
        assertEquals(listOf(SongMeaningResponse("0012a", 17)), response.song_meanings)
    }

    @Test
    fun changedChordTextIsAnUpdateWithTheSameImages() {
        val response = gson.fromJson(
            """{"tab_count":1,"chords_zip_size":123,"chord_markup_revision":"current"}""",
            CheckUpdateResponse::class.java
        )
        assertTrue(response.hasArchiveUpdate(1, 123, "previous"))
        assertFalse(response.hasArchiveUpdate(1, 123, "current"))
    }

    @Test
    fun updateMetadataSupportsLargeArchivesAndUnavailableValues() {
        val available = gson.fromJson(
            """{"tab_count":12,"chords_zip_size":3000000000}""",
            CheckUpdateResponse::class.java
        )
        val unavailable = gson.fromJson(
            """{"tab_count":null,"chords_zip_size":null}""",
            CheckUpdateResponse::class.java
        )

        assertEquals(CheckUpdateResponse(12, 3_000_000_000L), available)
        assertNull(unavailable.tab_count)
        assertNull(unavailable.chords_zip_size)
    }
}
