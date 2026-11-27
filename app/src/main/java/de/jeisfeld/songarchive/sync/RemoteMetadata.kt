package de.jeisfeld.songarchive.sync

data class SongResponse(
    val id: String,
    val title: String,
    val lyrics: String?,
    val lyrics_short: String?,
    val lyrics_paged: String?,
    val author: String?,
    val keywords: String?,
    val chordmarkup: String?,
    val tabfilename: String?,
    val mp3filename: String?,
    val mp3filename2: String?
)

data class MeaningResponse(
    val id: Int,
    val title: String,
    val meaning: String
)

data class SongMeaningResponse(
    val song_id: String,
    val meaning_id: Int
)

data class SyncResponse(
    val songs: List<SongResponse>,
    val meanings: List<MeaningResponse>,
    val song_meanings: List<SongMeaningResponse>,
    val chord_markup_revision: String? = null
)

data class CheckUpdateResponse(
    val tab_count: Int?,
    val chords_zip_size: Long?,
    val chord_markup_revision: String? = null
)

internal fun CheckUpdateResponse.hasArchiveUpdate(
    numberOfTabs: Int,
    chordsZipSize: Long,
    chordMarkupRevision: String? = null
): Boolean =
    (chord_markup_revision != null && chord_markup_revision != chordMarkupRevision) ||
        (tab_count != null && tab_count != numberOfTabs) ||
        (chords_zip_size?.let { it != chordsZipSize } ?: ((tab_count ?: 0) > 0))
