package com.gow3.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.gow3.android.data.SaveArchive

class SaveArchiveTest {

    private fun tempDir(): File {
        val dir = java.nio.file.Files.createTempDirectory("savearchive-test").toFile()
        dir.deleteOnExit()
        return dir
    }

    private fun makeSavedata(root: File): File {
        val savedata = File(root, "savedata").apply { mkdirs() }
        val slot = File(savedata, "1/CUSA01623/AUTOSAVE").apply { mkdirs() }
        File(slot, "data.bin").writeText("v1")
        File(File(slot, "AUTOSAVE.sce_sys").apply { mkdirs() }, "param.bin").writeText("p")
        File(File(savedata, "1/CUSA01623.memory").apply { mkdirs() }, "memory.dat").writeText("m")
        return savedata
    }

    private fun zipBytes(entries: List<Pair<String, String?>>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                if (content != null) zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun listFiles(dir: File): Map<String, String> =
        dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).invariantSeparatorsPath to it.readText() }

    @Test
    fun `hasSaves is true for a non-empty savedata tree`() {
        assertTrue(SaveArchive.hasSaves(makeSavedata(tempDir())))
    }

    @Test
    fun `hasSaves is false for missing and empty directories`() {
        val dir = tempDir()
        assertFalse(SaveArchive.hasSaves(File(dir, "missing")))
        File(dir, "savedata").mkdirs()
        assertFalse(SaveArchive.hasSaves(File(dir, "savedata")))
    }

    @Test
    fun `pack then extract round-trips the savedata tree`() {
        val dir = tempDir()
        val savedata = makeSavedata(dir)
        val zip = File(dir, "export.zip")
        SaveArchive.pack(savedata, zip)
        assertTrue(zip.isFile)

        val staging = File(dir, "savedata.restoring").apply { mkdirs() }
        SaveArchive.extract(zip.inputStream(), staging)
        assertEquals(listFiles(savedata), listFiles(staging))
    }

    @Test
    fun `validate accepts an archive produced by pack`() {
        val dir = tempDir()
        val zip = File(dir, "export.zip")
        SaveArchive.pack(makeSavedata(dir), zip)
        assertNull(SaveArchive.validate(zip.inputStream()))
    }

    @Test
    fun `validate rejects a file that is not a zip`() {
        val reason = SaveArchive.validate(ByteArrayInputStream("not a zip".toByteArray()))
        assertNotNull(reason)
    }

    @Test
    fun `validate rejects zip-slip paths`() {
        val bytes = zipBytes(listOf("savedata/1/CUSA01623/AUTOSAVE/../data.bin" to "x"))
        assertNotNull(SaveArchive.validate(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `validate rejects a mismatched title`() {
        val bytes = zipBytes(listOf("savedata/1/CUSA01740/AUTOSAVE/data.bin" to "x"))
        val reason = SaveArchive.validate(ByteArrayInputStream(bytes))
        assertNotNull(reason)
    }

    @Test
    fun `validate rejects a layout outside the savedata tree`() {
        val bytes = zipBytes(listOf("home/1/CUSA01623/data.bin" to "x"))
        assertNotNull(SaveArchive.validate(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `validate rejects an archive with no content files`() {
        val bytes = zipBytes(listOf("savedata/" to null, "savedata/1/" to null, "savedata/1/CUSA01623/" to null))
        assertNotNull(SaveArchive.validate(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `validate accepts the memory folder alongside the title dir`() {
        val bytes = zipBytes(
            listOf(
                "savedata/1/CUSA01623/AUTOSAVE/data.bin" to "x",
                "savedata/1/CUSA01623.memory/memory.dat" to "m",
            ),
        )
        assertNull(SaveArchive.validate(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `backupName appends a counter when the stamp is taken`() {
        val root = tempDir()
        File(root, "20240101-120000").mkdirs()
        File(root, "20240101-120000-2").mkdirs()
        assertEquals("20240101-120000-3", SaveArchive.backupName(root, "20240101-120000"))
    }

    @Test
    fun `replaceStaging keeps the original when the swap fails`() {
        val dir = tempDir()
        val savedata = File(dir, "savedata").apply { mkdirs() }
        File(savedata, "data.bin").writeText("v1")
        // staging 不存在，因此最终重命名必然失败
        val staging = File(dir, "savedata.restoring")
        val failed = try {
            SaveArchive.replaceStaging(staging, savedata)
            false
        } catch (_: IllegalStateException) {
            true
        }
        assertTrue(failed)
        assertEquals("v1", File(savedata, "data.bin").readText())
        assertFalse(File(dir, "savedata.old").exists())
    }
}
