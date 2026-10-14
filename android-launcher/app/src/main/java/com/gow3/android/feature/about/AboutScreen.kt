package com.gow3.android.feature.about

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gow3.android.data.LogArchive
import com.gow3.android.data.LogManager
import com.gow3.android.data.LogResult
import com.gow3.android.data.SaveArchive
import com.gow3.android.data.SaveManager
import com.gow3.android.data.SaveResult
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.ScreenColumn

@Composable
internal fun AboutScreen(saves: SaveManager, logs: LogManager) {
    val context = LocalContext.current
    var hasSaves by remember { mutableStateOf(SaveArchive.hasSaves(saves.savedataDir())) }
    var hasLogs by remember { mutableStateOf(LogArchive.hasLogs(logs.logsDir())) }

    fun refresh() {
        hasSaves = SaveArchive.hasSaves(saves.savedataDir())
        hasLogs = LogArchive.hasLogs(logs.logsDir())
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // 玩家可能在应用退到后台期间备份/恢复存档、安装运行时或运行游戏，
    // 因此每次 About 回到前台时都会从磁盘重新读取 savedata/日志状态。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ScreenColumn {
        Text("God of War III for Android", color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Version 1.2.4", color = LauncherColors.gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("A community launcher bringing the God of War III Remastered experience to Android via emulation.", color = LauncherColors.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(8.dp))
        ContactCard()
        SaveCard(
            hasSaves = hasSaves,
            onExport = {
                saves.launchExport { result ->
                    when (result) {
                        is SaveResult.Success -> toast(result.message)
                        SaveResult.Cancelled -> Unit
                        is SaveResult.Failed -> toast(result.message)
                    }
                }
            },
            onImport = {
                saves.launchImport { result ->
                    when (result) {
                        is SaveResult.Success -> {
                            refresh()
                            toast(result.message)
                        }
                        SaveResult.Cancelled -> Unit
                        is SaveResult.Failed -> toast(result.message)
                    }
                }
            },
        )
        LogCard(
            hasLogs = hasLogs,
            onExport = {
                logs.launchExport { result ->
                    when (result) {
                        is LogResult.Success -> {
                            refresh()
                            toast(result.message)
                        }
                        LogResult.Cancelled -> Unit
                        is LogResult.Failed -> toast(result.message)
                    }
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LauncherColors.surface)
                .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                .clickable { }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Third-party licenses", color = LauncherColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("›", color = LauncherColors.gold, fontSize = 24.sp)
        }
    }
}
