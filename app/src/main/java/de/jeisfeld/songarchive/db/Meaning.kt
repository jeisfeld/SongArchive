package de.jeisfeld.songarchive.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meaning")
data class Meaning(
    @PrimaryKey val id: Int,
    val title: String,
    val meaning: String
)
