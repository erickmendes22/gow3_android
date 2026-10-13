package com.gow3.android.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow3.android.data.LaunchParam
import com.gow3.android.data.LaunchParams
import com.gow3.android.ui.components.LauncherColors

@Composable
internal fun LaunchParameters(
    gameDir: String?,
    ini: Map<String, String>,
    save: (String, String) -> Unit,
    onChooseGameFolder: () -> Unit,
) {
    val groups = LaunchParams.all.groupBy { it.group }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.surface)
            .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("LAUNCH PARAMETERS", color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("GAME", color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onChooseGameFolder)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Game folder", color = LauncherColors.textMuted, fontSize = 13.sp)
                Text("CHANGE  ›", color = LauncherColors.gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
            }
            Text(
                gameDir ?: "Not selected",
                color = if (gameDir != null) LauncherColors.textPrimary else LauncherColors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Text("Tap to choose a different folder", color = LauncherColors.textMuted, fontSize = 10.sp)
        }
        groups.forEach { (title, params) ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(title, color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                params.forEach { param -> ParamRow(param, ini, save) }
            }
        }
        Text("Changes are saved to gow3.ini immediately and take effect at the next launch.", color = LauncherColors.textMuted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ParamRow(param: LaunchParam, values: Map<String, String>, save: (String, String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(param.label, color = LauncherColors.textMuted, fontSize = 13.sp)
        if (param.options.size == 2 && param.options[0].value == "0" && param.options[1].value == "1") {
            Switch(
                checked = LaunchParams.valueOf(values, param) == "1",
                onCheckedChange = { save(param.key, if (it) "1" else "0") },
            )
        } else {
            var expanded by remember { mutableStateOf(false) }
            Box {
                Text(
                    LaunchParams.labelOf(values, param) + "  ▾",
                    color = LauncherColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(LauncherColors.navSurface)
                        .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    param.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                expanded = false
                                save(param.key, option.value)
                            },
                        )
                    }
                }
            }
        }
    }
}
