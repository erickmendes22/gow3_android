package com.gow3.android.data

import java.io.File

/**
 * Safe reader/writer of gow3.ini (key=value lines, `#` comments), mirroring the semantics of
 * the Java frontend's SettingsDialog.load()/save(): a whole-file rewrite that keeps every other
 * line (comments, blanks, untouched keys), replaces changed keys in place, and appends missing
 * keys at the end. Writes go to a temp file that is atomically renamed, so a failure never
 * corrupts the original file. This is the launcher's only persistence channel for launch
 * parameters; the game (Gow3Settings::Load), run-gow3.sh and the in-game menu share the same file.
 */
internal object Gow3Ini {

    /** Parses key=value lines into a map; `#` comments and blank lines are ignored. Missing file -> empty. */
    fun read(path: File): Map<String, String> {
        if (!path.isFile) return emptyMap()
        val values = LinkedHashMap<String, String>()
        try {
            path.forEachLine { line ->
                val eq = line.indexOf('=')
                if (eq > 0 && !line.startsWith("#")) {
                    values[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return values
    }

    /**
     * Rewrites `path` whole with `updates` applied (changed keys replace their lines, missing keys
     * are appended; every other line is kept verbatim). Returns true on success; on any failure the
     * original file is left untouched and false is returned.
     */
    fun write(path: File, updates: Map<String, String>): Boolean {
        val lines = try {
            path.readLines(Charsets.UTF_8)
        } catch (_: Exception) {
            emptyList()
        }
        val pending = LinkedHashMap(updates)
        val out = mutableListOf<String>()
        for (line in lines) {
            val eq = line.indexOf('=')
            val key = if (eq > 0 && !line.startsWith("#")) line.substring(0, eq).trim() else null
            if (key != null && pending.containsKey(key)) {
                out.add("$key=${pending.remove(key)}")
            } else {
                out.add(line)
            }
        }
        for ((k, v) in pending) out.add("$k=$v")
        val temporary = File(path.path + ".tmp")
        return try {
            temporary.parentFile?.mkdirs()
            temporary.writeText(out.joinToString("\n") + "\n", Charsets.UTF_8)
            if (temporary.renameTo(path)) {
                true
            } else {
                temporary.delete()
                false
            }
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }
}
