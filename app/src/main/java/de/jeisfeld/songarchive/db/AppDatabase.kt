package de.jeisfeld.songarchive.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Song::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "songs.db"
                )
                    //.addCallback(SongDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

//    private class SongDatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
//        override fun onCreate(db: SupportSQLiteDatabase) {
//            super.onCreate(db)
//            INSTANCE?.let { database ->
//                scope.launch(Dispatchers.IO) {
//                    populateDatabase(database.songDao())
//                }
//            }
//        }
//
//        suspend fun populateDatabase(songDao: SongDao) {
//            // Insert test data
//            val songs = listOf(
//                Song("0001", "Imagine", "Lyrics", "John Lennon", "imagine.jpg"),
//                Song("0002", "Bohemian Rhapsody", "Lyrics", "Queen", "bohemian.jpg"),
//                Song("0003", "Hotel California", "Lyrics", "Eagles", "hotel.jpg")
//            )
//
//            scope.launch {
//                songDao.clearSongs()
//                songDao.insertSongs(songs)
//            }
//        }
//    }
}

