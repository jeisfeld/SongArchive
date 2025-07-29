package de.jeisfeld.songarchive.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_metadata")
data class AppMetadata(
    @PrimaryKey val id: Int = 1,  // Always only one row
    val numberOfTabs: Int,
    val chordsZipSize: Long,
    val language: String = "system",
    val defaultNetworkConnection: Int = 0
)