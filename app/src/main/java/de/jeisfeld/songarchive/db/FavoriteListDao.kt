package de.jeisfeld.songarchive.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
}
