package com.gow3.android.feature.about

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.gow3.android.ui.components.ActionButton
import com.gow3.android.ui.components.LauncherColors

private const val AUTHOR = "zfitness"
private const val QQ_GROUP = "1105902080"
private const val TELEGRAM_URL = "https://t.me/gow3_android"
private const val TELEGRAM_HANDLE = "@gow3_android"

/** 致谢移植作者，并引导玩家前往反馈群。 */
@Composable
internal fun ContactCard() {
    val context = LocalContext.current

    fun copy(label: String, value: String) {
        copyToClipboard(context, label, value)
        Toast.makeText(context, "$value copied", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LauncherColors.surface)
            .border(BorderStroke(1.dp, LauncherColors.gold.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("AUTHOR & SUPPORT", color = LauncherColors.gold, fontFamily = FontFamily.Serif, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ContactRow("Author", AUTHOR)
            ContactRow("QQ group", QQ_GROUP, "COPY") { copy("QQ group", QQ_GROUP) }
            ContactRow("Telegram", TELEGRAM_HANDLE, "OPEN") {
                if (!openUrl(context, TELEGRAM_URL)) {
                    Toast.makeText(context, "No app can open $TELEGRAM_URL", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Text(
            "Bug reports, crashes and feature requests are all welcome — attach the exported logs when you report an issue so it can be reproduced.",
            color = LauncherColors.textMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ContactRow(
    label: String,
    value: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, color = LauncherColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
            Text(value, color = LauncherColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (action != null) {
            ActionButton(action, false, onClick = onAction)
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

/** 在能够处理 [url] 的应用中打开它；当没有任何应用可以打开时（例如未安装浏览器）返回 false。 */
private fun openUrl(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        true
    } catch (error: ActivityNotFoundException) {
        false
    }
