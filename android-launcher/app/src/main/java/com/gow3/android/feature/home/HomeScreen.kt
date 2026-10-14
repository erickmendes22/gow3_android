package com.gow3.android.feature.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gow3.android.data.Gow3Ini
import com.gow3.android.data.ImportResult
import com.gow3.android.data.LosslessDllManager
import com.gow3.android.data.RemoveResult
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.ScreenColumn
import com.gow3.android.ui.components.formatBytes
import java.io.File

@Composable
internal fun HomeScreen(
    onStartGame: () -> Unit,
    losslessDlls: LosslessDllManager,
    onGetFromSteam: () -> Unit,
    onChooseGameFolder: () -> Unit,
) {
    val context = LocalContext.current
    var iniValues by remember { mutableStateOf(loadIni(context)) }
    var dllState by remember { mutableStateOf(losslessDlls.resolve()) }
    var gameDir by remember { mutableStateOf(currentGameDir(context)) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun refreshDll() {
        dllState = losslessDlls.resolve()
    }

    fun toast(message: String) {
        notice = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // 玩家可能在游戏运行期间把 Lossless.dll 放到游戏目录旁，因此每次 Home 回到前台
    // 时都会从磁盘重新读取状态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshDll()
                gameDir = currentGameDir(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun save(key: String, value: String) {
        if (Gow3Ini.write(gow3IniFile(context), mapOf(key to value))) {
            iniValues = iniValues + (key to value)
        } else {
            Toast.makeText(context, "Settings could not be saved", Toast.LENGTH_SHORT).show()
        }
    }

    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        Text("GOD OF WAR III", color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text("REMASTERED", color = LauncherColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
        Spacer(Modifier.height(28.dp))
        LaunchButton(onStartGame)
        Spacer(Modifier.height(20.dp))
        LaunchParameters(gameDir, iniValues, ::save, onChooseGameFolder)
        Spacer(Modifier.height(16.dp))
        FrameGenerationCard(
            state = dllState,
            notice = notice,
            onImport = {
                losslessDlls.launchPicker { result ->
                    when (result) {
                        is ImportResult.Success -> {
                            refreshDll()
                            toast("Imported ${LosslessDllManager.FILE_NAME} (${formatBytes(result.state.sizeBytes)})")
                        }
                        ImportResult.Cancelled -> refreshDll()
                        is ImportResult.Failed -> toast(result.message)
                    }
                }
            },
            onSteam = {
                onGetFromSteam()
                // 下载（当该构建附带下载时）在 launcher 外部进行；返回时重新读取，
                // 以便完成的下载无需手动下拉刷新即可显示。
            },
            onRemove = {
                when (val result = losslessDlls.remove()) {
                    is RemoveResult.Done -> {
                        refreshDll()
                        toast(
                            if (result.gameFolderRemains)
                                "Removed the launcher copy. The Lossless.dll beside the game folder is yours to delete."
                            else
                                "${LosslessDllManager.FILE_NAME} removed",
                        )
                    }
                    is RemoveResult.Failed -> toast(result.message)
                }
            },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** launcher 的 gow3.ini 路径，即游戏、run-gow3.sh 和 GameActivity 读取的同一个文件。 */
private fun gow3IniFile(context: Context): File = File(context.filesDir, "gow3port/gow3.ini")

private fun loadIni(context: Context): Map<String, String> = Gow3Ini.read(gow3IniFile(context))

private fun currentGameDir(context: Context): String? =
    context.getSharedPreferences("gow3", Context.MODE_PRIVATE).getString("game_dir", null)

@Composable
private fun LaunchButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFB8172B), Color(0xFF8C0D1A), Color(0xFFB8172B))))
            .border(BorderStroke(1.dp, LauncherColors.gold), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("LAUNCH", color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
        Text("START GAME", color = LauncherColors.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
    }
}
