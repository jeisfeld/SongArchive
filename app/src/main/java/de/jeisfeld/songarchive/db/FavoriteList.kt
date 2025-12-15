package de.jeisfeld.songarchive.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_lists")
data class FavoriteList(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isSorted: Boolean = false
)
