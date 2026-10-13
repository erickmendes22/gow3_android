package com.gow3.android.feature.mods

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow3.android.ui.components.EmptyState
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.ScreenColumn

@Composable
internal fun ModsScreen() {
    ScreenColumn {
        Text("MOD & PATCH", color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Manage game file mods and third-party shadPS4 patches for God of War III.", color = LauncherColors.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        EmptyState("NO MODS INSTALLED", "Your mod and patch list will appear here.")
    }
}
