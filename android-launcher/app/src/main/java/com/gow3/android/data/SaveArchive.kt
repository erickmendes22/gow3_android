package com.gow3.android.data

import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pure save-archive logic: packing the game's `savedata/` tree into a `.zip` that keeps the
 * `savedata/1/CUSA01623/...` prefix, validating such an archive (zip-slip-safe, layout- and
 * title-checked), extracting it into a staging directory, and naming backup folders.
 *
 * Kept free of Android dependencies so the host-side JVM tests can exercise it directly, in the
 * same spirit as [Gow3Ini]. This port supports a single title (God of War III, `CUSA01623`) and a
 * single user (`1`), so an archive is only accepted when every entry stays under that tree (the
 * `<title>.memory/` SaveDataMemory folder included).
 */
internal object SaveArchive {

    const val USER_ID = "1"
    const val TITLE_ID = "CUSA01623"
    private const val ROOT = "savedata"

    /** True when the directory holds at least one save file (the game's non-empty savedata tree). */
    fun hasSaves(savedata: File): Boolean =
        savedata.isDirectory && savedata.walkTopDown().any { it.isFile }

    /** `yyyyMMdd-HHmmss`, the same stamp layout the Linux-side save_backup.py uses. */
    fun stamp(now: Date = Date()): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)

    /** First free backup name under [backupsRoot]: the stamp, then `<stamp>-2`, `<stamp>-3`, ... */
    fun backupName(backupsRoot: File, stamp: String): String {
        var name = stamp
        var n = 2
        while (File(backupsRoot, name).exists()) {
            name = "$stamp-$n"
            n++
        }
        return name
    }

    /** Writes the whole [savedata] tree into [target] as a zip whose entries keep the `savedata/` prefix. */
    fun pack(savedata: File, target: File) {
        val root = savedata.parentFile ?: throw IllegalStateException("savedata has no parent directory")
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            savedata.walkTopDown().forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$relative/"))
                    zip.closeEntry()
                } else {
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Returns a failure reason, or null when [input] is a valid God of War III save archive. */
    fun validate(input: InputStream): String? {
        val seen = HashSet<String>()
        var contentFiles = 0
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val reason = validateEntry(name, seen)
                    if (reason != null) return reason
                    if (!entry.isDirectory) contentFiles++
                }
            }
        } catch (_: Exception) {
            return "所选文件不是有效的 zip 归档"
        }
        return if (contentFiles == 0) "归档中没有可导入的存档内容" else null
    }

    private fun validateEntry(name: String, seen: MutableSet<String>): String? {
        if (!isSafePath(name)) return "归档包含非法路径：$name"
        if (!seen.add(name)) return "归档包含重复路径：$name"
        if (!name.startsWith(ROOT)) return "归档结构不是存档布局：$name"
        if (!isSupportedName(name)) return "归档标题与 God of War III（$TITLE_ID）不匹配：$name"
        return null
    }

    /** Rejects absolute paths, `..` and empty segments (zip-slip guard). */
    private fun isSafePath(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\\')) return false
        // A trailing slash marks a directory entry and is not an empty segment.
        return name.trimEnd('/').split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    /** Accepts the `savedata/` structural tree and everything under `savedata/1/CUSA01623(/)` or `.memory`. */
    private fun isSupportedName(name: String): Boolean {
        val parts = name.trimEnd('/').split('/')
        return when {
            parts.size == 1 -> parts[0] == ROOT
            parts[0] != ROOT || parts[1] != USER_ID -> false
            parts.size == 2 -> true
            parts[2] == TITLE_ID || parts[2] == "$TITLE_ID.memory" -> true
            else -> false
        }
    }

    /** Extracts a validated archive into [staging] (which will become the new `savedata`). */
    fun extract(input: InputStream, staging: File) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == ROOT || name == "$ROOT/") continue
                val relative = name.removePrefix("$ROOT/")
                val target = File(staging, relative)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zip.copyTo(it) }
                }
            }
        }
    }

    /**
     * Swaps [staging] in as [savedata]. The current tree is renamed aside first, so a failure in the
     * final rename restores the original and never leaves an empty `savedata` behind.
     */
    fun replaceStaging(staging: File, savedata: File) {
        val old = File(savedata.parentFile, "savedata.old")
        old.deleteRecursively()
        if (savedata.exists() && !savedata.renameTo(old)) throw IllegalStateException("无法移开现有存档")
        if (!staging.renameTo(savedata)) {
            if (old.exists()) old.renameTo(savedata)
            throw IllegalStateException("无法应用导入的存档")
        }
        old.deleteRecursively()
    }
}
