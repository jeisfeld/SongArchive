package de.jeisfeld.songarchive.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {
    @Query("SELECT * FROM app_metadata WHERE id = 1")
    suspend fun get(): AppMetadata?

    @Query("SELECT * FROM app_metadata WHERE id = 1")
    fun observe(): Flow<AppMetadata?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: AppMetadata)
}