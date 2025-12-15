package de.jeisfeld.songarchive.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Song::class, Meaning::class, SongMeaning::class, AppMetadata::class, FavoriteList::class, FavoriteListSong::class],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun favoriteListDao(): FavoriteListDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_list_song` (`listId` INTEGER NOT NULL, `songId` TEXT NOT NULL, PRIMARY KEY(`listId`, `songId`), FOREIGN KEY(`listId`) REFERENCES `favorite_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`songId`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_list_song_listId` ON `favorite_list_song`(`listId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_favorite_list_song_songId` ON `favorite_list_song`(`songId`)")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_metadata ADD COLUMN language TEXT NOT NULL DEFAULT 'system'"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_metadata ADD COLUMN defaultNetworkConnection INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favorite_lists ADD COLUMN isSorted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE favorite_list_song ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE favorite_list_song ADD COLUMN customTitle TEXT")
                database.execSQL(
                    """
                        UPDATE favorite_list_song
                        SET position = ordering.new_position
                        FROM (
                            SELECT rowid AS rid, ROW_NUMBER() OVER (PARTITION BY listId ORDER BY songId) - 1 AS new_position
                            FROM favorite_list_song
                        ) AS ordering
                        WHERE favorite_list_song.rowid = ordering.rid
                    """.trimIndent()
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
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}

