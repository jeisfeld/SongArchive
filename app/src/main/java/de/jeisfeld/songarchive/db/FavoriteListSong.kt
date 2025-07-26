package de.jeisfeld.songarchive.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "favorite_list_song",
    primaryKeys = ["listId", "songId"],
    foreignKeys = [
        ForeignKey(entity = FavoriteList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["listId"]), Index(value = ["songId"])]
)
data class FavoriteListSong(
    val listId: Int,
    val songId: String
)
