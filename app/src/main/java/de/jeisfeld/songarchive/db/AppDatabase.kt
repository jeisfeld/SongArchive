package de.jeisfeld.songarchive.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Song::class, Meaning::class, SongMeaning::class, AppMetadata::class, FavoriteList::class, FavoriteListSong::class],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun favoriteListDao(): FavoriteListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_metadata ADD COLUMN language TEXT NOT NULL DEFAULT 'system'"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "songs.db"
                )
                    .addMigrations(MIGRATION_12_13)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}

