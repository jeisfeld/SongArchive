package de.jeisfeld.songarchive.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "lyrics")  // "text" ist ein reserviertes Wort in SQL, daher umbenannt
    val lyrics: String?,

    @ColumnInfo(name = "author")
    val author: String?,

    @ColumnInfo(name = "tabfilename")
    val tabfilename: String?
)