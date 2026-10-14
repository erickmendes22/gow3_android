package com.gow3.android.feature.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.gow3.android.ui.components.ActionButton
import com.gow3.android.ui.components.LauncherColors

/** 将 launcher 的 God of War III 运行时日志（例如 android-game.log）导出为 zip。 */
@Composable
internal fun LogCard(
    hasLogs: Boolean,
    onExport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.surface)
            .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("RUNTIME LOGS", color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (hasLogs) "Logs found" else "No logs yet",
                color = LauncherColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Export the God of War III runtime logs to a .zip file for troubleshooting. The game's log (android-game.log) lives in the app's private folder.",
                color = LauncherColors.textMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("EXPORT LOGS", hasLogs, Modifier.weight(1f), onExport)
        }
        Text(
            "Exporting only reads the runtime log files; it never modifies them or grabs the system logcat.",
            color = LauncherColors.textMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}