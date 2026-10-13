package com.gow3.android.data

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream

/** Outcome of one save export/import attempt. Cancelled means the user backed out of the picker. */
internal sealed class SaveResult {
    data class Success(val message: String) : SaveResult()
    object Cancelled : SaveResult()
    data class Failed(val message: String) : SaveResult()
}

/**
 * Owns the God of War III saves: exporting the `savedata/` tree to a `.zip` the player picks via
 * Storage Access Framework, and importing such an archive back with validation, a safety backup and
 * an atomic swap. Only `filesDir/gow3port/user/savedata` and its `savedata_backups` are touched; the
 * runtime, gow3.ini, runtime install and game files are never modified.
 */
internal class SaveManager(private val activity: Activity) {

    companion object {
        /** Distinct from GameFolderManager's 1001/1002 and LosslessDllManager's 1003. */
        const val REQUEST_EXPORT = 1004
        const val REQUEST_IMPORT = 1005
        private const val DEFAULT_EXPORT_NAME = "GodOfWar3-Saves"
    }

    /** Set by [launchExport]/[launchImport], consumed by [onActivityResult]. */
    private var pending: ((SaveResult) -> Unit)? = null

    /** The game's savedata, the directory run-gow3.sh passes to the game as `--user .../user/savedata`. */
    fun savedataDir(): File = File(activity.filesDir, "gow3port/user/savedata")

    /** Safety backups made before every import, mirroring the Linux-side savedata_backups layout. */
    fun savedataBackupsDir(): File = File(activity.filesDir, "gow3port/user/savedata_backups")

    /**
     * Opens the system save-as picker. When there are no saves, [onResult] is called immediately
     * with a failure and no picker is shown. On any other outcome the result arrives through
     * [onActivityResult] from the activity's legacy callback.
     */
    fun launchExport(onResult: (SaveResult) -> Unit) {
        if (!SaveArchive.hasSaves(savedataDir())) {
            onResult(SaveResult.Failed("暂无存档可导出"))
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "$DEFAULT_EXPORT_NAME-${SaveArchive.stamp()}.zip")
        }
        pending = onResult
        try {
            activity.startActivityForResult(intent, REQUEST_EXPORT)
        } catch (ex: Exception) {
            pending = null
            onResult(SaveResult.Failed("No file picker is available on this device (${ex.message ?: "unknown error"})."))
        }
    }

    /**
     * Opens the system file picker for a save archive. [onResult] always runs: the user cancelling,
     * no picker being installed, a rejected archive and a failed swap all come back as a
     * [SaveResult], never as a throw.
     */
    fun launchImport(onResult: (SaveResult) -> Unit) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        pending = onResult
        try {
            activity.startActivityForResult(intent, REQUEST_IMPORT)
        } catch (ex: Exception) {
            pending = null
            onResult(SaveResult.Failed("No file picker is available on this device (${ex.message ?: "unknown error"})."))
        }
    }

    /** @return true when the request was ours and has been consumed. */
    fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
        val isExport = requestCode == REQUEST_EXPORT
        val isImport = requestCode == REQUEST_IMPORT
        if (!isExport && !isImport) return false
        val callback = pending
        pending = null
        if (callback == null) return true
        val uri = intent?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            callback(SaveResult.Cancelled)
            return true
        }
        Thread {
            val result = if (isExport) exportTo(uri) else importFrom(uri)
            activity.runOnUiThread { callback(result) }
        }.start()
        return true
    }

    /**
     * Packs the savedata tree into a temp zip first (SAF streams are not seekable), then copies it
     * into the picker's URI. The temp file is always deleted, success or failure.
     */
    private fun exportTo(uri: Uri): SaveResult {
        val savedata = savedataDir()
        if (!SaveArchive.hasSaves(savedata)) return SaveResult.Failed("暂无存档可导出")
        val temporary = File(activity.filesDir, "gow3port/.export-saves.tmp")
        temporary.delete()
        return try {
            SaveArchive.pack(savedata, temporary)
            val output = activity.contentResolver.openOutputStream(uri)
                ?: return SaveResult.Failed("无法写入所选位置，未改动任何存档。")
            output.use { out ->
                FileInputStream(temporary).use { it.copyTo(out) }
            }
            SaveResult.Success("存档已导出：${displayName(uri) ?: "所选位置"}")
        } catch (ex: Exception) {
            SaveResult.Failed("存档导出失败：${ex.message ?: "未知错误"}")
        } finally {
            temporary.delete()
        }
    }

    /** Validates, extracts to staging, backs up the current saves and swaps them in. */
    private fun importFrom(uri: Uri): SaveResult {
        val user = File(activity.filesDir, "gow3port/user")
        val staging = File(user, "savedata.restoring")
        return try {
            val first = activity.contentResolver.openInputStream(uri)
                ?: return SaveResult.Failed("无法读取所选文件。")
            val problem = first.use { SaveArchive.validate(it) }
            if (problem != null) return SaveResult.Failed(problem)

            staging.deleteRecursively()
            val second = activity.contentResolver.openInputStream(uri)
                ?: return SaveResult.Failed("无法读取所选文件。")
            second.use { SaveArchive.extract(it, staging) }

            val savedata = savedataDir()
            val backup = backupExisting(savedata)
            SaveArchive.replaceStaging(staging, savedata)
            SaveResult.Success(
                if (backup != null) "存档导入成功，导入前的存档已备份到 ${backup.name}。"
                else "存档导入成功。",
            )
        } catch (ex: Exception) {
            staging.deleteRecursively()
            SaveResult.Failed("存档导入失败：${ex.message ?: "未知错误"}")
        }
    }

    /** Copies the current saves to a timestamped backup folder; null when there was nothing to back up. */
    private fun backupExisting(savedata: File): File? {
        if (!SaveArchive.hasSaves(savedata)) return null
        val root = savedataBackupsDir()
        if (!root.isDirectory && !root.mkdirs()) return null
        val target = File(root, SaveArchive.backupName(root, SaveArchive.stamp()))
        if (!savedata.copyRecursively(target)) return null
        return target
    }

    /** The provider's display name, falling back to the URI's last segment. */
    private fun displayName(uri: Uri): String? {
        try {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(column)
                    if (!name.isNullOrEmpty()) return name
                }
            }
        } catch (_: Exception) {
            // Fall through to the URI segment.
        }
        val segment = uri.lastPathSegment ?: return null
        return segment.substringAfterLast('/').takeIf { it.isNotEmpty() }
    }
}
