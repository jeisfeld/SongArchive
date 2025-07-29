package de.jeisfeld.songarchive.utils

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LanguageUtil {
    fun applyAppLanguage(language: String) {
        val locales = when (language) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            "de" -> LocaleListCompat.forLanguageTags("de")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
