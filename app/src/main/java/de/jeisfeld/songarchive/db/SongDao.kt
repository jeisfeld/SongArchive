package de.jeisfeld.songarchive.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("""
        SELECT *, 
            CASE 
                WHEN title_normalized LIKE :fullQuery THEN 1 ELSE 0 
            END AS full_title_match,
            CASE 
                WHEN lyrics_normalized LIKE :fullQuery THEN 1 ELSE 0 
            END AS full_lyrics_match,
            -- Count how many search words appear in title
            (
                CASE WHEN title_normalized LIKE :word1 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word2 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word3 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word4 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word5 THEN 1 ELSE 0 END
            ) AS title_match_count,
            
            -- Count how many search words appear in lyrics
            (
                CASE WHEN lyrics_normalized LIKE :word1 THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word2 THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word3 THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word4 THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word5 THEN 1 ELSE 0 END
            ) AS lyrics_match_count
        FROM songs
        WHERE 
            (id LIKE :query OR 
            title_normalized LIKE :query OR 
            lyrics_normalized LIKE :query OR 
            author_normalized LIKE :query OR 
            keywords_normalized LIKE :query) 
            OR (
                (title_normalized LIKE :word1 OR
                lyrics_normalized LIKE :word1 OR
                author_normalized LIKE :word1 OR
                keywords_normalized LIKE :word1)
                AND
                (title_normalized LIKE :word2 OR
                lyrics_normalized LIKE :word2 OR
                author_normalized LIKE :word2 OR
                keywords_normalized LIKE :word2)
                AND
                (title_normalized LIKE :word3 OR
                lyrics_normalized LIKE :word3 OR
                author_normalized LIKE :word3 OR
                keywords_normalized LIKE :word3)
                AND
                (title_normalized LIKE :word4 OR
                lyrics_normalized LIKE :word4 OR
                author_normalized LIKE :word4 OR
                keywords_normalized LIKE :word4)
                AND
                (title_normalized LIKE :word5 OR
                lyrics_normalized LIKE :word5 OR
                author_normalized LIKE :word5 OR
                keywords_normalized LIKE :word5)
            )
        ORDER BY 
            full_title_match DESC,
            full_lyrics_match DESC,
            title_match_count DESC,
            lyrics_match_count DESC,
            id ASC
    """)
    fun searchSongs(
        query: String,
        fullQuery: String,
        word1: String,
        word2: String,
        word3: String,
        word4: String,
        word5: String
    ): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("DELETE FROM songs")
    suspend fun clearSongs()

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<Song>
}

