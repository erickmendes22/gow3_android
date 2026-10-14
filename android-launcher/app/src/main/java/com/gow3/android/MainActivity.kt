package com.gow3.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.gow3.android.data.GameFolderManager
import com.gow3.android.data.LogManager
import com.gow3.android.data.LosslessDllManager
import com.gow3.android.data.SaveManager
import com.gow3.android.ui.LauncherScreen
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.theme.Gow3LauncherTheme

class MainActivity : ComponentActivity() {
    private lateinit var gameFolderManager: GameFolderManager
    private lateinit var losslessDllManager: LosslessDllManager
    private lateinit var saveManager: SaveManager
    private lateinit var logManager: LogManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = LauncherColors.backgroundTop.toArgbCompat()
        window.navigationBarColor = LauncherColors.backgroundBottom.toArgbCompat()
        window.decorView.systemUiVisibility = 0

        gameFolderManager = GameFolderManager(this)
        losslessDllManager = LosslessDllManager(this)
        saveManager = SaveManager(this)
        logManager = LogManager(this)
        setContent {
            Gow3LauncherTheme(dynamicColor = false) {
                LauncherScreen(
                    onStartGame = {
                        if (gameFolderManager.hasGameFolder()) {
                            startActivity(Intent(this, ro.cobrabm.fexdroid.GameActivity::class.java))
                        }
                    },
                    losslessDlls = losslessDllManager,
                    onGetFromSteam = {
                        ro.cobrabm.fexdroid.SteamDllDialog(this, losslessDllManager.appCopy()).show()
                    },
                    onChooseGameFolder = { gameFolderManager.chooseGameFolder() },
                    saves = saveManager,
                    logs = logManager,
                )
            }
        }
        gameFolderManager.ensureGameFolder()
    }

    @Deprecated("Legacy Activity Result API is required by this target SDK flow")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        gameFolderManager.onPermissionResult(requestCode, grantResults)
    }

    @Deprecated("Legacy Activity Result API is required by this target SDK flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (saveManager.onActivityResult(requestCode, resultCode, data)) return
        if (logManager.onActivityResult(requestCode, resultCode, data)) return
        if (losslessDllManager.onPickerResult(requestCode, resultCode, data)) return
        gameFolderManager.onFolderResult(requestCode, resultCode, data)
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
