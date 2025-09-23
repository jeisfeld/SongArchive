package de.jeisfeld.songarchive.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Utility functions to handle tab files selected from local storage.
 */
object LocalTabUtils {
    private const val LOCAL_TAB_PREFIX = "local_uri:"

    /**
     * Returns true if the provided [tabFilename] references a locally selected tab file.
     */
    fun isLocalTab(tabFilename: String?): Boolean {
        return tabFilename?.startsWith(LOCAL_TAB_PREFIX) == true
    }

    /**
     * Wraps a locally selected tab [uriString] so it can be persisted in the database.
     */
    fun encodeLocalTab(uriString: String?): String? {
        return uriString?.takeIf { it.isNotBlank() }?.let { "$LOCAL_TAB_PREFIX$it" }
    }

    /**
     * Extracts the persisted URI string for a locally selected tab file.
     */
    fun decodeLocalTab(tabFilename: String?): String? {
        return tabFilename?.takeIf { isLocalTab(tabFilename) }?.removePrefix(LOCAL_TAB_PREFIX)
    }

    /**
     * Determines a human-readable display name for the locally selected tab file.
     */
    fun getDisplayName(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) {
            return null
        }
        val uri = Uri.parse(uriString)
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            } ?: uri.lastPathSegment ?: uriString
        } catch (e: Exception) {
            uri.lastPathSegment ?: uriString
        }
    }
}
