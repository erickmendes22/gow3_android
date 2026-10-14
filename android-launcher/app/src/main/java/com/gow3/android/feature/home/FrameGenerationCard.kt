package com.gow3.android.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow3.android.data.LosslessDllManager
import com.gow3.android.data.LosslessDllSource
import com.gow3.android.data.LosslessDllState
import com.gow3.android.ui.components.ActionButton
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.formatBytes

/**
 * 提供 Lossless.dll，即 lsfg-vk 帧生成所加载的玩家自备文件。LSFG 倍率、
 * flow scale 和性能模式在游戏内设置对话框中调节，存放在 gow3.ini 中；
 * 本卡片只负责 DLL 的来源以及是否已安装。
 */
@Composable
internal fun FrameGenerationCard(
    state: LosslessDllState,
    notice: String?,
    onImport: () -> Unit,
    onSteam: () -> Unit,
    onRemove: () -> Unit,
) {
    val installed = state as? LosslessDllState.Installed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.surface)
            .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("FRAME GENERATION", color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                LosslessDllManager.FILE_NAME,
                color = LauncherColors.textMuted,
                fontSize = 13.sp,
            )
            if (installed == null) {
                Text("Not installed", color = LauncherColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Frame generation needs the Lossless.dll from your own copy of Lossless Scaling. " +
                        "Import the file here, or get it from Steam.",
                    color = LauncherColors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            } else {
                Text(
                    installed.file.name + " · " + formatBytes(installed.sizeBytes),
                    color = LauncherColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    // 特意写成“launcher 文件夹”而非“已下载”：导入的文件和 Steam
                    // 下载的文件在磁盘上无法区分。
                    when (installed.source) {
                        LosslessDllSource.GAME_FOLDER -> "Beside your game folder — remove it there yourself"
                        LosslessDllSource.APP_PRIVATE -> "In the launcher's own folder"
                    },
                    color = LauncherColors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("IMPORT .DLL", installed == null, Modifier.weight(1f), onImport)
            ActionButton("GET FROM STEAM", installed == null, Modifier.weight(1f), onSteam)
        }
        if (installed != null) {
            ActionButton("REMOVE", false, Modifier.fillMaxWidth(), onRemove)
        }
        if (notice != null) {
            Text(notice, color = LauncherColors.gold, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Text(
            "Imported files stay in the app's private folder and take effect the next time the game starts. " +
                "IMPORT .DLL also works when Steam is unavailable.",
            color = LauncherColors.textMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}
