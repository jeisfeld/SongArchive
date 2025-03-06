package de.jeisfeld.songarchive.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,

    val title: String,
    val lyrics: String,
    val author: String,
    val keywords: String,
    val tabfilename: String?,
    val mp3filename: String?,
    val mp3filename2: String?,

    val title_normalized: String,
    val lyrics_normalized: String,
    val author_normalized: String,
    val keywords_normalized: String
)