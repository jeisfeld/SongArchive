package de.jeisfeld.songarchive.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query(
        """
        SELECT *, 
            CASE 
                WHEN title_normalized LIKE :fullQuery THEN 1 ELSE 0 
            END AS full_title_match,
            CASE 
                WHEN lyrics_normalized LIKE :fullQuery THEN 1 ELSE 0 
            END AS full_lyrics_match,
            CASE 
                WHEN keywords_normalized LIKE :fullQuery THEN 1 ELSE 0 
            END AS full_keywords_match,
            (
                CASE WHEN title_normalized LIKE :word1a THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word2a THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word3a THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word4a THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word5a THEN 1 ELSE 0 END
            ) AS title_word_match_count,
            (
                CASE WHEN author_normalized LIKE :word1a THEN 1 ELSE 0 END +
                CASE WHEN author_normalized LIKE :word2a THEN 1 ELSE 0 END +
                CASE WHEN author_normalized LIKE :word3a THEN 1 ELSE 0 END +
                CASE WHEN author_normalized LIKE :word4a THEN 1 ELSE 0 END +
                CASE WHEN author_normalized LIKE :word5a THEN 1 ELSE 0 END
            ) AS author_word_match_count,
            (
                CASE WHEN keywords_normalized LIKE :word1a THEN 1 ELSE 0 END +
                CASE WHEN keywords_normalized LIKE :word2a THEN 1 ELSE 0 END +
                CASE WHEN keywords_normalized LIKE :word3a THEN 1 ELSE 0 END +
                CASE WHEN keywords_normalized LIKE :word4a THEN 1 ELSE 0 END +
                CASE WHEN keywords_normalized LIKE :word5a THEN 1 ELSE 0 END
            ) AS keywords_word_match_count,
            (
                CASE WHEN lyrics_normalized LIKE :word1a THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word2a THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word3a THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word4a THEN 1 ELSE 0 END +
                CASE WHEN lyrics_normalized LIKE :word5a THEN 1 ELSE 0 END
            ) AS lyrics_word_match_count,
            (
                CASE WHEN title_normalized LIKE :word1 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word2 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word3 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word4 THEN 1 ELSE 0 END +
                CASE WHEN title_normalized LIKE :word5 THEN 1 ELSE 0 END
            ) AS title_match_count,
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
            full_keywords_match DESC,
            title_word_match_count DESC,
            lyrics_word_match_count DESC,
            author_word_match_count DESC,
            keywords_word_match_count DESC,
            title_match_count DESC,
            lyrics_match_count DESC,
            id ASC
    """
    )
    fun searchSongs(
        query: String,
        fullQuery: String,
        word1: String,
        word1a: String,
        word2: String,
        word2a: String,
        word3: String,
        word3a: String,
        word4: String,
        word4a: String,
        word5: String,
        word5a: String
    ): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Query("DELETE FROM songs")
    suspend fun clearSongs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeanings(songs: List<Meaning>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongMeanings(songs: List<SongMeaning>)

    @Query("DELETE FROM meaning")
    suspend fun clearMeanings()

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT count(*) FROM songs")
    suspend fun getSongCount(): Int

    @Query(
        """SELECT m.* FROM meaning m 
           INNER JOIN song_meaning sm ON m.id = sm.meaningId 
           WHERE sm.songId = :songId"""
    )
    suspend fun getMeaningsForSong(songId: String): List<Meaning>
}

