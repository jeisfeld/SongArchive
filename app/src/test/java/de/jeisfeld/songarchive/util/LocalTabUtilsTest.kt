package de.jeisfeld.songarchive.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTabUtilsTest {
    @Test
    fun selectedDocumentUriRoundTripsInTheStoredFormat() {
        val uri = "content://example.documents/document/Sheet%20Music%2Fmorning.jpg"
        val stored = LocalTabUtils.encodeLocalTab(uri)

        assertEquals("local_uri:$uri", stored)
        assertTrue(LocalTabUtils.isLocalTab(stored))
        assertEquals(uri, LocalTabUtils.decodeLocalTab(stored))
    }

    @Test
    fun archiveFilenameIsNotTreatedAsALocalDocument() {
        assertFalse(LocalTabUtils.isLocalTab("morning.jpg"))
        assertNull(LocalTabUtils.decodeLocalTab("morning.jpg"))
    }

    @Test
    fun missingOrBlankSelectionsHaveNoStoredValue() {
        for (selection in listOf(null, "", " \t")) {
            assertNull(LocalTabUtils.encodeLocalTab(selection))
            assertFalse(LocalTabUtils.isLocalTab(selection))
            assertNull(LocalTabUtils.decodeLocalTab(selection))
        }
    }
}
