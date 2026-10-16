package com.gow3.android.data

import java.io.File
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** The ten requested patches only; resolution and engine FPS stay on Home. */
internal enum class PatchGroup { VIDEO, FIX, EFFECT }
internal data class GamePatch(val name: String, val group: PatchGroup, val enabledByDefault: Boolean)
internal class PatchCatalog(val titleId: String, val version: String, val patches: List<GamePatch>) {
    val selectionKey = "patches_${titleId}_${version.replace('.', '_')}"
    fun selected(ini: Map<String, String>): Set<String> {
        val selected = if (selectionKey in ini)
            ini.getValue(selectionKey).split(';').filter { it.isNotBlank() }.toSet()
        else patches.filter { it.enabledByDefault }.map { it.name }.toSet()
        return if (ini["render_resolution"] != null && ini["render_resolution"] != "native")
            selected - "Bug Fix - Texture Corruption Fix" else selected
    }
    fun toggle(ini: Map<String, String>, name: String, enabled: Boolean): Map<String, String> {
        require(patches.any { it.name == name })
        val updated = selected(ini).toMutableSet()
        if (enabled) updated.add(name) else updated.remove(name)
        val changes = mutableMapOf("restart_unconfirmed" to "0")
        if (name == "Bug Fix - Texture Corruption Fix" && enabled) {
            // Texture video arena and resolution video arena are exclusive.
            changes["render_resolution"] = "native"
        }
        changes[selectionKey] = patches.filter { it.name in updated }
            .joinToString(";") { it.name }
        return changes
    }
    companion object {
        private val allowed = listOf(
            "Skip Intro" to PatchGroup.VIDEO,
            "Skip Any Video With X Button" to PatchGroup.VIDEO,
            "Bug Fix - Texture Corruption Fix" to PatchGroup.FIX,
            "Bug Fix - Startup Black Screen" to PatchGroup.FIX,
            "Disable SSAO" to PatchGroup.EFFECT,
            "Disable Motion Blur" to PatchGroup.EFFECT,
            "Disable Depth of Field" to PatchGroup.EFFECT,
            "Disable Bloom" to PatchGroup.EFFECT,
            "Disable Soft Particles" to PatchGroup.EFFECT,
            "Disable Dynamic Shadows" to PatchGroup.EFFECT
        )
        private val supportedTitles = setOf("CUSA01623","CUSA01715","CUSA01720")
        fun load(game: File?, update: File?, open: (String) -> InputStream): PatchCatalog? {
            if (game == null || !File(game,"eboot.bin").isFile) return null
            val base = GameSfo.read(File(game,"sce_sys/param.sfo")) ?: return null
            val effective = if (update != null) {
                val updated = GameSfo.read(File(update,"sce_sys/param.sfo")) ?: return null
                if (!File(update,"eboot.bin").isFile || updated["TITLE_ID"] != base["TITLE_ID"])
                    return null
                updated
            } else base
            val title=effective["TITLE_ID"] ?: return null
            val version=effective["APP_VER"] ?: return null
            if (title !in supportedTitles || version!="01.02") return null
            return open("God_of_War_III_Remastered.xml").use { input ->
                val doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
                val metas=doc.getElementsByTagName("Metadata")
                val byName=(0 until metas.length).map { metas.item(it) as Element }
                    .filter { it.getAttribute("AppVer")==version }
                    .associateBy { it.getAttribute("Name") }
                val patches=allowed.mapNotNull { (name,group) ->
                    byName[name]?.let {
                        GamePatch(name,group,it.getAttribute("isEnabled")=="true")
                    }
                }
                if (patches.size!=allowed.size) null else PatchCatalog(title,version,patches)
            }
        }
    }
}
