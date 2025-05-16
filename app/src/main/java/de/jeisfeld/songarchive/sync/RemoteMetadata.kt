package de.jeisfeld.songarchive.sync

data class SongResponse(
    val id: String,
    val title: String,
    val lyrics: String?,
    val lyrics_short: String?,
    val lyrics_paged: String?,
    val author: String?,
    val keywords: String?,
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
    val song_meanings: List<SongMeaningResponse>
)

data class CheckUpdateResponse(
   val tab_count: Int?,
   val chords_zip_size: Long?
)
