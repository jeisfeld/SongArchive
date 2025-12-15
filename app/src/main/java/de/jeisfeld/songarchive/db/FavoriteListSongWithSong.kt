package de.jeisfeld.songarchive.db

import androidx.room.Embedded
import androidx.room.Relation

data class FavoriteListSongWithSong(
    @Embedded val entry: FavoriteListSong,
    @Relation(parentColumn = "songId", entityColumn = "id")
    val song: Song,
)
