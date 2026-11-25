package de.jeisfeld.songarchive.sync

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipFile

internal fun installChordArchive(filesDir: File, input: InputStream): Long {
    val staging = Files.createTempDirectory(filesDir.toPath(), "chord-sync-").toFile()
    var keepStaging = false
    try {
        val archive = File(staging, "chords.zip")
        archive.outputStream().use { input.copyTo(it) }
        val extracted = File(staging, "images")
        if (!extracted.mkdir()) throw IOException("Could not create the chord staging directory")

        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                val image = File(extracted, entry.name).canonicalFile
                if (entry.isDirectory || image.parentFile != extracted.canonicalFile) {
                    throw IOException("Invalid entry in chord archive: ${entry.name}")
                }
                CheckedInputStream(zip.getInputStream(entry), CRC32()).use { source ->
                    image.outputStream().use { source.copyTo(it) }
                    if (image.length() != entry.size || source.checksum.value != entry.crc) {
                        throw IOException("Corrupt entry in chord archive: ${entry.name}")
                    }
                }
            }
        }

        val installed = File(filesDir, "chords")
        val previous = File(staging, "previous")
        if (installed.exists() && !installed.renameTo(previous)) {
            throw IOException("Could not stage the previous chord images")
        }
        if (!extracted.renameTo(installed)) {
            if (previous.exists() && !previous.renameTo(installed)) {
                keepStaging = true
                throw IOException("Could not restore the previous chord images")
            }
            throw IOException("Could not install the new chord images")
        }
        return archive.length()
    } finally {
        if (!keepStaging) staging.deleteRecursively()
    }
}
