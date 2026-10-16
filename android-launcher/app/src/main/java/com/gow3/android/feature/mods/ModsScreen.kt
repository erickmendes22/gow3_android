package com.gow3.android.feature.mods

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gow3.android.R
import com.gow3.android.data.GamePatch
import com.gow3.android.data.Gow3Ini
import com.gow3.android.data.PatchCatalog
import com.gow3.android.data.PatchGroup
import com.gow3.android.ui.components.EmptyState
import com.gow3.android.ui.components.LauncherColors
import com.gow3.android.ui.components.ScreenColumn
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun patchNameRes(name:String): Int=when(name) {
    "Skip Intro" -> R.string.patch_skip_intro
    "Skip Any Video With X Button" -> R.string.patch_skip_video
    "Bug Fix - Texture Corruption Fix" -> R.string.patch_texture_fix
    "Bug Fix - Startup Black Screen" -> R.string.patch_black_screen
    "Disable SSAO" -> R.string.patch_ssao
    "Disable Motion Blur" -> R.string.patch_blur
    "Disable Depth of Field" -> R.string.patch_dof
    "Disable Bloom" -> R.string.patch_bloom
    "Disable Soft Particles" -> R.string.patch_particles
    "Disable Dynamic Shadows" -> R.string.patch_shadows
    else -> error("Unsupported patch $name")
}
@Composable
internal fun ModsScreen() {
    val context=LocalContext.current
    val prefs=remember(context) { context.getSharedPreferences("gow3",Context.MODE_PRIVATE) }
    val config=remember(context) { File(context.filesDir,"gow3port/gow3.ini") }
    var revision by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<PatchCatalog?>(null) }
    var ini by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var notice by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope()
    fun load(): PatchCatalog? = PatchCatalog.load(
        prefs.getString("game_dir",null)?.let(::File),
        prefs.getString("update_dir",null)?.let(::File),
        context.assets::open
    )
    DisposableEffect(prefs,lifecycle) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key=="game_dir" || key=="update_dir") revision++
        }
        val observer=LifecycleEventObserver { _,event ->
            if (event==Lifecycle.Event.ON_RESUME) revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(revision) {
        val result=withContext(Dispatchers.IO) {
            runCatching { load() to Gow3Ini.read(config) }
        }
        result.onSuccess { (c,v) -> catalog=c; ini=v }
            .onFailure { catalog=null; notice=R.string.mods_no_game }
    }
    fun toggle(current:PatchCatalog,patch:GamePatch,enabled:Boolean) {
        saving=true
        notice=null
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                runCatching {
                    check(load()?.selectionKey==current.selectionKey)
                    check(Gow3Ini.write(config,current.toggle(Gow3Ini.read(config),patch.name,enabled)))
                    Gow3Ini.read(config)
                }
            }
            result.onSuccess { ini=it }.onFailure { notice=R.string.mods_save_failed }
            saving=false
        }
    }
    ScreenColumn {
        Text(stringResource(R.string.mods_title),color=LauncherColors.textPrimary,
            fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=24.sp)
        Text(stringResource(R.string.mods_description),color=LauncherColors.textMuted,fontSize=13.sp)
        notice?.let { Text(stringResource(it),color=LauncherColors.gold,fontSize=12.sp) }
        val current=catalog
        if (current==null) {
            EmptyState(stringResource(R.string.mods_title),stringResource(R.string.mods_no_game))
        } else {
            Text("${current.titleId} · ${current.version}",color=LauncherColors.gold,fontSize=12.sp)
            Text(stringResource(R.string.mods_next_launch),color=LauncherColors.textMuted,fontSize=12.sp)
            val selected=current.selected(ini)
            for(group in PatchGroup.entries) {
                val label=when(group) {
                    PatchGroup.VIDEO -> R.string.mods_group_video
                    PatchGroup.FIX -> R.string.mods_group_fixes
                    PatchGroup.EFFECT -> R.string.mods_group_effects
                }
                Text(stringResource(label),color=LauncherColors.gold,fontSize=14.sp,
                    fontWeight=FontWeight.SemiBold)
                for(patch in current.patches.filter { it.group==group }) {
                    Surface(color=LauncherColors.surface,shape=RoundedCornerShape(12.dp),
                        border=BorderStroke(1.dp,LauncherColors.gold.copy(alpha=0.45f))) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(patchNameRes(patch.name)),
                                    color=LauncherColors.textPrimary,fontSize=14.sp,
                                    fontWeight=FontWeight.SemiBold)
                                Text(stringResource(R.string.mods_checked_offline),
                                    color=LauncherColors.textMuted,fontSize=11.sp)
                            }
                            Switch(checked=patch.name in selected,enabled=!saving,
                                onCheckedChange={toggle(current,patch,it)},
                                colors=SwitchDefaults.colors(
                                    checkedTrackColor=LauncherColors.crimson,
                                    checkedThumbColor=LauncherColors.gold))
                        }
                    }
                }
            }
        }
    }
}
