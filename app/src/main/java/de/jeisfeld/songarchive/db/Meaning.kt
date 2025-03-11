package de.jeisfeld.songarchive.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.android.parcel.Parcelize

@Entity(tableName = "meaning")
@Parcelize
data class Meaning(
    @PrimaryKey val id: Int,
    val title: String,
    val meaning: String
): Parcelable
