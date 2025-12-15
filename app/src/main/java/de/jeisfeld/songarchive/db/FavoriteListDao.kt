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

    @Query("SELECT * FROM favorite_lists WHERE id = :id")
    suspend fun getById(id: Int): FavoriteList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: FavoriteList): Long

    @Update
    suspend fun update(list: FavoriteList)

    @Delete
    suspend fun delete(list: FavoriteList)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(entry: FavoriteListSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(entries: List<FavoriteListSong>)

    @Update
    suspend fun updateSong(entry: FavoriteListSong)

    @Query("DELETE FROM favorite_list_song WHERE listId = :listId AND songId = :songId")
    suspend fun deleteSongFromList(listId: Int, songId: String)

    @Query("SELECT listId FROM favorite_list_song WHERE songId = :songId")
    suspend fun getListsForSong(songId: String): List<Int>

    @Query("SELECT * FROM favorite_list_song")
    suspend fun getAllEntries(): List<FavoriteListSong>

    @Query("SELECT s.* FROM songs s INNER JOIN favorite_list_song fls ON s.id = fls.songId WHERE fls.listId = :listId ORDER BY fls.position, s.id")
    suspend fun getSongsForList(listId: Int): List<Song>

    @Transaction
    @Query("SELECT * FROM favorite_list_song WHERE listId = :listId ORDER BY position, songId")
    suspend fun getSongEntries(listId: Int): List<FavoriteListSongWithSong>

    @Query("SELECT COALESCE(MAX(position), -1) FROM favorite_list_song WHERE listId = :listId")
    suspend fun getMaxPosition(listId: Int): Int

    @Query("UPDATE favorite_list_song SET position = :position WHERE listId = :listId AND songId = :songId")
    suspend fun updatePosition(listId: Int, songId: String, position: Int)

    @Query("UPDATE favorite_list_song SET customTitle = :customTitle WHERE listId = :listId AND songId = :songId")
    suspend fun updateCustomTitle(listId: Int, songId: String, customTitle: String?)
}
