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
 * 纯存档归档逻辑：将游戏的 `savedata/` 目录树打包成保留 `savedata/1/CUSA01623/...`
 * 前缀的 `.zip`，校验此类归档（防 zip-slip、校验布局和标题）、解压到暂存目录，
 * 并为备份目录命名。
 *
 * 保持不依赖 Android，以便宿主端 JVM 测试可以直接验证它，与 [Gow3Ini] 思路一致。
 * 本移植只支持单一标题（God of War III，`CUSA01623`）和单一用户（`1`），因此
 * 只有当所有条目都位于该目录树之下（包括 `<title>.memory/` 的 SaveDataMemory 目录）
 * 时，归档才会被接受。
 */
internal object SaveArchive {

    const val USER_ID = "1"
    const val TITLE_ID = "CUSA01623"
    private const val ROOT = "savedata"

    /** 当目录中至少含一个存档文件（游戏非空的 savedata 目录树）时为 true。 */
    fun hasSaves(savedata: File): Boolean =
        savedata.isDirectory && savedata.walkTopDown().any { it.isFile }

    /** `yyyyMMdd-HHmmss`，与 Linux 侧 save_backup.py 使用相同的时间戳格式。 */
    fun stamp(now: Date = Date()): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)

    /** [backupsRoot] 下第一个可用的备份名：时间戳，然后是 `<stamp>-2`、`<stamp>-3`…… */
    fun backupName(backupsRoot: File, stamp: String): String {
        var name = stamp
        var n = 2
        while (File(backupsRoot, name).exists()) {
            name = "$stamp-$n"
            n++
        }
        return name
    }

    /** 将整个 [savedata] 目录树写入 [target] 的 zip，条目保留 `savedata/` 前缀。 */
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

    /** 当 [input] 是有效的 God of War III 存档归档时返回 null，否则返回失败原因。 */
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

    /** 拒绝绝对路径、`..` 和空段（zip-slip 防护）。 */
    private fun isSafePath(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.contains('\\')) return false
        // 结尾斜杠表示目录条目，并非空段。
        return name.trimEnd('/').split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    /** 接受 `savedata/` 结构树以及 `savedata/1/CUSA01623(/)` 或 `.memory` 下的所有内容。 */
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

    /** 将已校验的归档解压到 [staging]（它将成为新的 `savedata`）。 */
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
     * 用 [staging] 替换 [savedata]。先把当前目录树重命名到一边，因此最终重命名
     * 失败时可以恢复原目录，绝不会留下空的 `savedata`。
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
