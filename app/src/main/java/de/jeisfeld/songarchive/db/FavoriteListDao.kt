package de.jeisfeld.songarchive.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import de.jeisfeld.songarchive.db.FavoriteListSong
import de.jeisfeld.songarchive.db.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteListDao {
    @Query("SELECT * FROM favorite_lists ORDER BY id")
    fun getAll(): Flow<List<FavoriteList>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: FavoriteList): Long

    @Update
    suspend fun update(list: FavoriteList)

    @Delete
    suspend fun delete(list: FavoriteList)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(entry: FavoriteListSong)

    @Query("DELETE FROM favorite_list_song WHERE listId = :listId AND songId = :songId")
    suspend fun deleteSongFromList(listId: Int, songId: String)

    @Query("SELECT listId FROM favorite_list_song WHERE songId = :songId")
    suspend fun getListsForSong(songId: String): List<Int>

    @Query("SELECT s.* FROM songs s INNER JOIN favorite_list_song fls ON s.id = fls.songId WHERE fls.listId = :listId ORDER BY s.id")
    suspend fun getSongsForList(listId: Int): List<Song>
}
