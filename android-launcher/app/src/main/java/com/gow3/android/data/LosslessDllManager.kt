package com.gow3.android.data

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Where the effective Lossless.dll was found, in the order the runtime resolves it. */
internal enum class LosslessDllSource { GAME_FOLDER, APP_PRIVATE }

/** What the launcher currently has installed, re-read from disk on every call to `resolve()`. */
internal sealed class LosslessDllState {
    object NotInstalled : LosslessDllState()
    data class Installed(val file: File, val sizeBytes: Long, val source: LosslessDllSource) : LosslessDllState()
}

/** Outcome of one `IMPORT .DLL` attempt. Cancelled means the user backed out of the picker. */
internal sealed class ImportResult {
    data class Success(val state: LosslessDllState.Installed) : ImportResult()
    object Cancelled : ImportResult()
    data class Failed(val message: String) : ImportResult()
}

/** Outcome of one `REMOVE`. The game-folder copy is never touched, only reported. */
internal sealed class RemoveResult {
    data class Done(val state: LosslessDllState, val gameFolderRemains: Boolean) : RemoveResult()
    data class Failed(val message: String) : RemoveResult()
}

/**
 * Owns the player's `Lossless.dll`: where the runtime looks for it, importing one the player
 * picked themselves, and telling the UI which copy is in effect.
 *
 * The lookup order is the single source of truth shared with the game runtime: the copy beside
 * the game folder wins, `filesDir/gow3port/Lossless.dll` is the fallback that both the Steam
 * download and the manual import write to. No third location is consulted, and nothing outside
 * the app-private directory is ever written or deleted — the player's game folder is read-only
 * to this class.
 */
internal class LosslessDllManager(private val activity: Activity) {

    companion object {
        /** Distinct from GameFolderManager's 1001 (folder) and 1002 (storage permission). */
        const val REQUEST_LOSSESS_DLL = 1003
        const val FILE_NAME = "Lossless.dll"
    }

    /** Set by [launchPicker], consumed by [onPickerResult]; a cancel or a second launch clears it. */
    private var pending: ((ImportResult) -> Unit)? = null

    /** Where Steam downloads and manual imports both land: the runtime's fallback location. */
    fun appCopy(): File = File(activity.filesDir, "gow3port/$FILE_NAME")

    /** The copy beside the game folder, or null when no game folder is chosen or it has no parent. */
    fun besideGameFolder(): File? {
        val dir = activity.getSharedPreferences("gow3", Activity.MODE_PRIVATE).getString("game_dir", null) ?: return null
        return try {
            File(dir).parentFile?.let { File(it, FILE_NAME) }
        } catch (_: Exception) {
            null
        }
    }

    fun resolve(): LosslessDllState {
        besideGameFolder()?.let { if (it.isFile) return installed(it, LosslessDllSource.GAME_FOLDER) }
        val own = appCopy()
        if (own.isFile) return installed(own, LosslessDllSource.APP_PRIVATE)
        return LosslessDllState.NotInstalled
    }

    /**
     * Opens the system file picker. [onResult] always runs: the user cancelling, no picker being
     * installed, a rejected file and a failed copy all come back as an [ImportResult], never as a
     * throw. The result arrives through [onPickerResult] from the activity's legacy callback.
     */
    fun launchPicker(onResult: (ImportResult) -> Unit) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        pending = onResult
        try {
            activity.startActivityForResult(intent, REQUEST_LOSSESS_DLL)
        } catch (ex: Exception) {
            pending = null
            onResult(ImportResult.Failed("No file picker is available on this device (${ex.message ?: "unknown error"})."))
        }
    }

    /** @return true when the request was ours and has been consumed. */
    fun onPickerResult(requestCode: Int, resultCode: Int, intent: Intent?): Boolean {
        if (requestCode != REQUEST_LOSSESS_DLL) return false
        val callback = pending
        pending = null
        if (callback == null) return true
        val uri = intent?.data
        if (resultCode != Activity.RESULT_OK || uri == null) callback(ImportResult.Cancelled) else callback(importFrom(uri))
        return true
    }

    /**
     * Copies a player-supplied DLL to [appCopy]. Validation is by name, non-emptiness and
     * readability only: the launcher ships no copy to compare against, and lsfg-vk — not us —
     * decides which Lossless build loads, so parsing the PE header here would only risk
     * rejecting a valid file.
     */
    fun importFrom(uri: Uri): ImportResult {
        val name = displayName(uri)
        if (name == null) return ImportResult.Failed("That file has no readable name.")
        if (!name.equals(FILE_NAME, ignoreCase = true)) return ImportResult.Failed("That is not $FILE_NAME (selected: $name).")

        val size = sizeOf(uri)
        if (size != null && size <= 0L) return ImportResult.Failed("$FILE_NAME is empty.")

        val target = copyInto(uri) ?: return ImportResult.Failed("$FILE_NAME could not be written. Nothing was changed.")
        return ImportResult.Success(installed(target, LosslessDllSource.APP_PRIVATE))
    }

    /**
     * Deletes the app-private copy only. When the game-folder copy is the effective one the player
     * has to delete it themselves, so the caller is told via [RemoveResult.Done.gameFolderRemains]
     * and the resulting state still reports it as installed.
     */
    fun remove(): RemoveResult {
        val own = appCopy()
        if (own.exists() && !own.delete()) return RemoveResult.Failed("$FILE_NAME could not be removed.")
        val state = resolve()
        return RemoveResult.Done(state, state is LosslessDllState.Installed && state.source == LosslessDllSource.GAME_FOLDER)
    }

    /**
     * Writes to a temporary file in the same directory and renames it over the target, so a failure
     * mid-copy can never leave the runtime reading a truncated Lossless.dll. rename(2) replaces
     * the destination atomically.
     */
    private fun copyInto(source: Uri): File? {
        val target = appCopy()
        val parent = target.parentFile ?: return null
        if (!parent.isDirectory && !parent.mkdirs()) return null
        val temporary = File(parent, ".$FILE_NAME.tmp")
        temporary.delete()
        try {
            val input = activity.contentResolver.openInputStream(source) ?: return null
            input.use { stream -> temporary.outputStream().use { stream.copyTo(it) } }
        } catch (_: Exception) {
            temporary.delete()
            return null
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            return null
        }
        return target
    }

    private fun installed(file: File, source: LosslessDllSource): LosslessDllState.Installed =
        LosslessDllState.Installed(file, if (file.isFile) file.length() else 0L, source)

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
            // Fall through to the URI segment: some providers refuse the query.
        }
        val segment = uri.lastPathSegment ?: return null
        return segment.substringAfterLast('/').takeIf { it.isNotEmpty() }
    }

    /** Null when the provider does not report a size; the readable check below still applies. */
    private fun sizeOf(uri: Uri): Long? =
        try {
            activity.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) cursor.getLong(column) else null
            }
        } catch (_: Exception) {
            null
        }
}