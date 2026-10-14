package com.gow3.android.data

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/** 负责首次运行时的游戏目录校验、存储权限和目录选择器状态。 */
internal class GameFolderManager(private val activity: Activity) {
    companion object {
        const val REQUEST_GAME_FOLDER = 1001
        const val REQUEST_STORAGE = 1002
        private val SERIALS = arrayOf("CUSA01740", "CUSA00900", "CUSA00207", "CUSA00208", "CUSA00299",
            "CUSA01363", "CUSA03179", "CUSA03014", "CUSA03023")
    }

    fun hasGameFolder(): Boolean {
        if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return false
        val path = activity.getSharedPreferences("gow3", Activity.MODE_PRIVATE).getString("game_dir", null) ?: return false
        return isGame(File(path))
    }

    fun ensureGameFolder() {
        if (hasGameFolder()) return
        chooseGameFolder()
    }

    /** 重新打开目录选择器，让玩家可以将游戏指向另一个目录。 */
    fun chooseGameFolder() {
        if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
        } else {
            showFolderDialog()
        }
    }

    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_STORAGE) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showFolderDialog()
        } else {
            AlertDialog.Builder(activity)
                .setTitle("Storage permission required")
                .setMessage("God of War III needs storage access to read the game folder.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    fun onFolderResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_GAME_FOLDER) return
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            ensureGameFolder()
            return
        }
        var dir = folderOf(data.data!!)
        if (dir != null && !isGame(dir)) {
            for (serial in SERIALS) {
                val nested = File(dir, serial)
                if (isGame(nested)) {
                    dir = nested
                    break
                }
            }
        }
        if (!isGame(dir)) {
            AlertDialog.Builder(activity)
                .setTitle("Not a God of War III folder")
                .setMessage((dir?.path ?: "That location") + " has no eboot.bin.")
                .setPositiveButton("Choose again") { _, _ -> showFolderDialog() }
                .show()
            return
        }
        activity.getSharedPreferences("gow3", Activity.MODE_PRIVATE).edit()
            .putString("game_dir", dir!!.path)
            .apply()
    }

    private fun showFolderDialog() {
        val current = activity.getSharedPreferences("gow3", Activity.MODE_PRIVATE).getString("game_dir", null)
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(24))
            background = roundedBackground(0xff171411.toInt(), 0xffc6ad76.toInt(), 20)
        }
        val eyebrow = TextView(activity).apply {
            text = "GAME SETUP"
            textSize = 11f
            letterSpacing = 0.18f
            setTextColor(0xffc6ad76.toInt())
        }
        val title = TextView(activity).apply {
            text = "Choose your game folder"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xffe8dfc9.toInt())
            setPadding(0, dp(7), 0, 0)
        }
        val message = TextView(activity).apply {
            text = "Select the extracted God of War III folder, such as CUSA01740, or its parent folder. The selected location must contain eboot.bin." +
                (if (current != null) "\n\nCurrent selection:\n$current" else "")
            textSize = 14f
            setTextColor(0xff8e9097.toInt())
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(12), 0, dp(20))
        }
        val choose = TextView(activity).apply {
            text = "CHOOSE GAME FOLDER"
            gravity = Gravity.CENTER
            textSize = 13f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xffe8dfc9.toInt())
            background = roundedBackground(0xffb8172b.toInt(), 0xffc6ad76.toInt(), 12)
            setPadding(0, dp(15), 0, dp(15))
            isFocusable = true
        }
        val keep = if (current != null) TextView(activity).apply {
            text = "KEEP CURRENT SELECTION"
            gravity = Gravity.CENTER
            textSize = 13f
            letterSpacing = 0.08f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xff8e9097.toInt())
            background = roundedBackground(0xff12100d.toInt(), 0xffc6ad76.toInt(), 12)
            setPadding(0, dp(13), 0, dp(13))
            isFocusable = true
        } else null
        panel.addView(eyebrow, LinearLayout.LayoutParams(-1, -2))
        panel.addView(title, LinearLayout.LayoutParams(-1, -2))
        panel.addView(message, LinearLayout.LayoutParams(-1, -2))
        panel.addView(choose, LinearLayout.LayoutParams(-1, dp(52)))
        if (keep != null) {
            val params = LinearLayout.LayoutParams(-1, -2)
            params.topMargin = dp(10)
            panel.addView(keep, params)
        }

        // 首次设置不可被取消；当从 Home 页重新选择时已存在有效目录，
        // 因此允许玩家在不做任何修改的情况下退出。
        val dialog = AlertDialog.Builder(activity).setView(panel).setCancelable(current != null).create()
        choose.setOnClickListener {
            dialog.dismiss()
            try {
                activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_GAME_FOLDER)
            } catch (ex: Exception) {
                AlertDialog.Builder(activity).setMessage("No folder picker: $ex").setPositiveButton("OK", null).show()
            }
        }
        keep?.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(dp(350), LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun isGame(dir: File?): Boolean = dir != null && File(dir, "eboot.bin").isFile

    private fun folderOf(tree: Uri): File? {
        return try {
            val id = android.provider.DocumentsContract.getTreeDocumentId(tree)
            val colon = id.indexOf(':')
            val volume = if (colon < 0) id else id.substring(0, colon)
            val path = if (colon < 0) "" else id.substring(colon + 1)
            val base = if ("primary".equals(volume, ignoreCase = true)) Environment.getExternalStorageDirectory()
            else File("/storage/$volume")
            if (path.isEmpty()) base else File(base, path)
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    private fun roundedBackground(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setCornerRadius(dp(radius).toFloat())
            setStroke(dp(1), stroke)
        }
}
