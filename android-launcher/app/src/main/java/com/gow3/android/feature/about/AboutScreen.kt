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
import com.gow3.android.data.SaveArchive
import com.gow3.android.data.SaveManager
import com.gow3.android.data.SaveResult
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.ScreenColumn

@Composable
internal fun AboutScreen(saves: SaveManager) {
    val context = LocalContext.current
    var hasSaves by remember { mutableStateOf(SaveArchive.hasSaves(saves.savedataDir())) }

    fun refreshSaves() {
        hasSaves = SaveArchive.hasSaves(saves.savedataDir())
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // The player may back up/restore saves or install them while the app is backgrounded, so the
    // savedata state is re-read from disk every time About comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshSaves()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ScreenColumn {
        Text("God of War III for Android", color = LauncherColors.textPrimary, fontFamily = FontFamily.Serif, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Version 1.2.4", color = LauncherColors.gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("A community launcher bringing the God of War III Remastered experience to Android via emulation.", color = LauncherColors.textMuted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(8.dp))
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
                            refreshSaves()
                            toast(result.message)
                        }
                        SaveResult.Cancelled -> Unit
                        is SaveResult.Failed -> toast(result.message)
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
