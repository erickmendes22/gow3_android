package com.gow3.android.feature.mods

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gow3.android.R
import com.gow3.android.ui.components.EmptyState
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.ScreenColumn

@Composable
internal fun ModsScreen() {
    ScreenColumn {
        Text(stringResource(R.string.mods_title), color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.mods_description), color = LauncherColors.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        EmptyState(stringResource(R.string.mods_empty_title), stringResource(R.string.mods_empty_description))
    }
}
