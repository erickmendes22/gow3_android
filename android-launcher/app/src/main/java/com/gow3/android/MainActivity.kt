package com.gow3.android

import android.content.Context
import android.content.Intent
import android.app.AlertDialog
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.gow3.android.data.GameFolderManager
import com.gow3.android.data.LanguageManager
import com.gow3.android.data.LogManager
import com.gow3.android.data.LosslessDllManager
import com.gow3.android.data.SaveManager
import com.gow3.android.data.PkgInstallManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.gow3.android.ui.LauncherScreen
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.theme.Gow3LauncherTheme

class MainActivity : ComponentActivity() {
    private lateinit var gameFolderManager: GameFolderManager
    private lateinit var losslessDllManager: LosslessDllManager
    private lateinit var saveManager: SaveManager
    private lateinit var logManager: LogManager
    private lateinit var pkgInstallManager: PkgInstallManager
    private val pkgBusy = mutableStateOf(false)
    private val pkgMessage = mutableStateOf<String?>(null)
    private val pkgGeneration = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLanguage(newBase))
    }

    /** 按 LanguageManager 的语言覆盖（或系统语言）为 MainActivity 注入 Locale 配置。 */
    private fun applyLanguage(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(LanguageManager.resolve(base))
        return base.createConfigurationContext(config)
    }

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
        pkgInstallManager = PkgInstallManager(this) { busy, message ->
            pkgBusy.value = busy
            pkgMessage.value = message
            if (!busy) pkgGeneration.intValue += 1
        }
        setContent {
            Gow3LauncherTheme(dynamicColor = false) {
                LauncherScreen(
                    onStartGame = {
                        if (gameFolderManager.hasGameFolder()) {
                            startActivity(Intent(this, ro.cobrabm.fexdroid.GameActivity::class.java))
                        }
                    },
                    losslessDlls = losslessDllManager,
                    onGetFromSteam = { onDownloaded ->
                        ro.cobrabm.fexdroid.SteamDllDialog(this, losslessDllManager.appCopy()) { ok, msg ->
                            onDownloaded(ok, msg)
                        }.show()
                    },
                    onChooseGameFolder = { gameFolderManager.chooseGameFolder() },
                    onChooseUpdateFolder = { gameFolderManager.chooseUpdateFolder() },
                    onInstallBasePkg = { pkgInstallManager.pickBasePkg() },
                    onInstallUpdatePkg = { pkgInstallManager.pickUpdatePkg() },
                    pkgInstallBusy = pkgBusy.value,
                    pkgInstallMessage = pkgMessage.value,
                    pkgGeneration = pkgGeneration.intValue,
                    saves = saveManager,
                    logs = logManager,
                    onChangeLanguage = { value ->
                        LanguageManager.set(this, value)
                        recreate()
                    },
                )
            }
        }
        // First run (or a previously selected folder that no longer exists):
        // present BOTH supported game sources, rather than opening the old
        // folder-only picker or leaving the user with no setup dialog.
        // Posting after setContent ensures the launcher window is attached.
        if (savedInstanceState == null && !gameFolderManager.hasGameFolder()) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed && !gameFolderManager.hasGameFolder()) {
                    showFirstRunGameSource()
                }
            }
        }
    }

    private fun showFirstRunGameSource() {
        AlertDialog.Builder(this)
            .setTitle("Configurar God of War III")
            .setMessage("Como deseja adicionar o jogo? Você pode instalar um PKG diretamente " +
                "numa pasta escolhida ou selecionar uma pasta já extraída.")
            .setPositiveButton("Instalar PKG") { _, _ -> pkgInstallManager.pickBasePkg() }
            .setNegativeButton("Selecionar pasta") { _, _ -> gameFolderManager.chooseGameFolder() }
            .setCancelable(false)
            .show()
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
        if (pkgInstallManager.onPickerResult(requestCode, resultCode, data)) return
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
