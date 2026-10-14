package com.gow3.android.data

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream

/** 一次日志导出尝试的结果。Cancelled 表示用户在文件选择器中退出了。 */
internal sealed class LogResult {
    data class Success(val message: String) : LogResult()
    object Cancelled : LogResult()
    data class Failed(val message: String) : LogResult()
}

/**
 * 负责 God of War III 运行时日志：将 `logs/` 目录树（例如 GameActivity 写入的
 * `android-game.log`）导出为玩家通过 Storage Access Framework 选择的 `.zip`。
 * 只读取 `filesDir/gow3port/logs`；运行时、gow3.ini、运行时安装、存档和游戏文件
 * 绝不被修改。导出是只读快照，因此导出期间运行游戏只会捕获当时已刷盘的字节。
 */
internal class LogManager(private val activity: Activity) {

    companion object {
        /** 区别于 GameFolderManager 的 1001/1002、LosslessDllManager 的 1003 和 SaveManager 的 1004/1005。 */
        const val REQUEST_EXPORT = 1006
        private const val DEFAULT_EXPORT_NAME = "GodOfWar3-Logs"
    }

    /** 由 [launchExport] 设置，由 [onActivityResult] 消费。 */
    private var pending: ((LogResult) -> Unit)? = null

    /** launcher 的运行时日志目录，即 GameActivity 写入 `android-game.log` 的目录。 */
    fun logsDir(): File = File(activity.filesDir, "gow3port/logs")

    /**
     * 打开系统“另存为”选择器。当没有日志时，立即以失败调用 [onResult]，且不显示
     * 选择器。其他情况下，结果通过 activity 的 legacy 回调经由 [onActivityResult] 到达。
     */
    fun launchExport(onResult: (LogResult) -> Unit) {
        if (!LogArchive.hasLogs(logsDir())) {
            onResult(LogResult.Failed("暂无日志可导出"))
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "$DEFAULT_EXPORT_NAME-${LogArchive.stamp()}.zip")
        }
        pending = onResult
        try {
            activity.startActivityForResult(intent, REQUEST_EXPORT)
        } catch (ex: Exception) {
            pending = null
            onResult(LogResult.Failed("No file picker is available on this device (${ex.message ?: "unknown error"})."))
        }
    }

    /** @return 当请求属于我们且已被消费时返回 true。 */
    fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
        if (requestCode != REQUEST_EXPORT) return false
        val callback = pending
        pending = null
        if (callback == null) return true
        val uri = intent?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            callback(LogResult.Cancelled)
            return true
        }
        Thread {
            val result = exportTo(uri)
            activity.runOnUiThread { callback(result) }
        }.start()
        return true
    }

    /**
     * 先把日志目录树打包成临时 zip（SAF 流不可 seek），再复制到选择器返回的 URI。
     * 无论成功或失败，临时文件始终会被删除。
     */
    private fun exportTo(uri: Uri): LogResult {
        val logs = logsDir()
        if (!LogArchive.hasLogs(logs)) return LogResult.Failed("暂无日志可导出")
        val temporary = File(activity.filesDir, "gow3port/.export-logs.tmp")
        temporary.delete()
        return try {
            LogArchive.pack(logs, temporary)
            val output = activity.contentResolver.openOutputStream(uri)
                ?: return LogResult.Failed("无法写入所选位置，日志未被修改。")
            output.use { out ->
                FileInputStream(temporary).use { it.copyTo(out) }
            }
            LogResult.Success("日志已导出：${displayName(uri) ?: "所选位置"}")
        } catch (ex: Exception) {
            LogResult.Failed("日志导出失败：${ex.message ?: "未知错误"}")
        } finally {
            temporary.delete()
        }
    }

    /** Provider 的显示名称，回退到 URI 的最后一段。 */
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
            // 回退到 URI 段。
        }
        val segment = uri.lastPathSegment ?: return null
        return segment.substringAfterLast('/').takeIf { it.isNotEmpty() }
    }
}