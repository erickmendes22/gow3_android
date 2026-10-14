package com.gow3.android.feature.about

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
import com.gow3.android.ui.components.ActionButton
import com.gow3.android.ui.components.LauncherColors

/** 将玩家的 God of War III 存档备份为 zip，并在带安全备份的情况下恢复一个备份。 */
@Composable
internal fun SaveCard(
    hasSaves: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
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
        Text("SAVE DATA", color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (hasSaves) "Saves found" else "No saves yet",
                color = LauncherColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Back up your God of War III saves to a .zip file, or restore a backup. The game's savedata lives in the app's private folder.",
                color = LauncherColors.textMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton("EXPORT SAVE", hasSaves, Modifier.weight(1f), onExport)
            ActionButton("IMPORT SAVE", false, Modifier.weight(1f), onImport)
        }
        Text(
            "Importing replaces the current saves, but the existing ones are backed up first into the app's private folder.",
            color = LauncherColors.textMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}
