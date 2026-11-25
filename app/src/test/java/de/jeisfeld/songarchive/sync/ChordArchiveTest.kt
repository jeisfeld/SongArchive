package de.jeisfeld.songarchive.sync

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChordArchiveTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun installationReturnsDownloadedSizeAndMakesNewImagesAvailableImmediately() {
        val images = files.newFolder("chords")
        File(images, "old.jpg").writeText("old image")
        val archive = archiveWithImage()

        assertEquals(archive.size.toLong(), installChordArchive(files.root, archive.inputStream()))
        assertEquals(listOf("new.jpg"), images.list()?.toList())
        assertEquals("new image", File(images, "new.jpg").readText())
        assertEquals(listOf("chords"), files.root.list()?.toList())
    }

    @Test
    fun invalidAndTruncatedDownloadsPreserveInstalledImages() {
        val images = files.newFolder("chords")
        File(images, "old.jpg").writeText("old image")
        val archive = archiveWithImage()

        for (invalid in listOf("Server error".toByteArray(), archive.copyOf(archive.size - 10))) {
            assertThrows(IOException::class.java) {
                installChordArchive(files.root, invalid.inputStream())
            }
            assertEquals(listOf("old.jpg"), images.list()?.toList())
            assertEquals("old image", File(images, "old.jpg").readText())
        }
    }

    @Test
    fun missingServerArchiveWithChordSheetsRequiresSyncEvenWhenTabCountIsUnchanged() {
        assertTrue(CheckUpdateResponse(5, null).hasArchiveUpdate(numberOfTabs = 5, chordsZipSize = 123))
        assertFalse(CheckUpdateResponse(0, null).hasArchiveUpdate(numberOfTabs = 0, chordsZipSize = 0))
        assertFalse(CheckUpdateResponse(null, null).hasArchiveUpdate(numberOfTabs = 5, chordsZipSize = 123))
    }

    @Test
    fun installedSizeAndTabCountDetermineWhetherTheNextStartupNeedsSync() {
        assertFalse(CheckUpdateResponse(5, 123).hasArchiveUpdate(numberOfTabs = 5, chordsZipSize = 123))
        assertTrue(CheckUpdateResponse(5, 234).hasArchiveUpdate(numberOfTabs = 5, chordsZipSize = 123))
        assertTrue(CheckUpdateResponse(6, 123).hasArchiveUpdate(numberOfTabs = 5, chordsZipSize = 123))
    }

    private fun archiveWithImage(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("new.jpg"))
            zip.write("new image".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }
}
