package de.jeisfeld.songarchive.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    suspend fun searchSongs(query: String): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("DELETE FROM songs")
    suspend fun clearSongs()

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<Song>
}

