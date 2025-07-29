package de.jeisfeld.songarchive

import android.app.Application
import de.jeisfeld.songarchive.db.AppDatabase
import de.jeisfeld.songarchive.db.AppMetadata
import de.jeisfeld.songarchive.utils.LanguageUtil
import kotlinx.coroutines.runBlocking

class SongArchiveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(this).appMetadataDao()
        runBlocking {
            val metadata = dao.get() ?: AppMetadata(numberOfTabs = 0, chordsZipSize = 0, language = "system")
            LanguageUtil.applyAppLanguage(metadata.language)
        }
    }
}
