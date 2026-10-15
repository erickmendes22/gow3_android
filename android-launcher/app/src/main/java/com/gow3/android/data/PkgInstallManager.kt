package com.gow3.android.data

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs PS4 base and update PKGs using the bundled native orbis-pkg-util.
 *
 * SAF gives read permission only for the selected PKG. The original is NEVER
 * modified. Extraction happens on the destination filesystem into a hidden
 * staging folder, then is published by rename. Updates are kept separately:
 * the runtime already combines base/update via game_update.py.
 */
internal class PkgInstallManager(
    private val activity: Activity,
    private val onState: (busy: Boolean, message: String?) -> Unit,
) {
    companion object {
        const val REQUEST_BASE_PKG = 1008
        const val REQUEST_UPDATE_PKG = 1009
        private val TITLE_PATTERN = Regex("CUSA[0-9]{5}", RegexOption.IGNORE_CASE)
    }

    private val working = AtomicBoolean(false)
    private val queue = Executors.newSingleThreadExecutor()
    private val preferences get() = activity.getSharedPreferences("gow3", Activity.MODE_PRIVATE)

    fun pickBasePkg() = pick(false)
    fun pickUpdatePkg() = pick(true)

    private fun pick(update: Boolean) {
        if (working.get()) {
            Toast.makeText(activity, "Uma instalação PKG já está em andamento", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // file pickers do not consistently expose a .pkg MIME type
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, if (update) REQUEST_UPDATE_PKG else REQUEST_BASE_PKG)
    }

    fun onPickerResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request != REQUEST_BASE_PKG && request != REQUEST_UPDATE_PKG) return false
        if (result != Activity.RESULT_OK || data?.data == null) return true
        val uri = data.data!!
        if (!working.compareAndSet(false, true)) return true
        val update = request == REQUEST_UPDATE_PKG
        onState(true, if (update) "Instalando atualização PKG…" else "Instalando jogo PKG…")
        val progress = AlertDialog.Builder(activity)
            .setTitle(if (update) "Atualização do jogo" else "Instalação do jogo")
            .setMessage("Verificando o pacote e extraindo para armazenamento local…")
            .setCancelable(false)
            .create()
        progress.show()
        queue.execute {
            val outcome = runCatching { install(uri, update) }
            activity.runOnUiThread {
                working.set(false)
                progress.dismiss()
                val message = outcome.fold({ it }, { "PKG não instalado: ${it.message ?: it.javaClass.simpleName}" })
                onState(false, message)
                AlertDialog.Builder(activity)
                    .setTitle(if (outcome.isSuccess) "PKG instalado" else "Falha ao instalar PKG")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        return true
    }

    private fun install(uri: Uri, update: Boolean): String {
        val root = File(activity.getExternalFilesDir(null) ?: activity.filesDir, "installed-ps4")
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Sem permissão para criar ${root.path}")
        val helper = File(activity.filesDir, "gow3port/arm64/bin/orbis-pkg-util")
        val loader = File(activity.filesDir,
            "rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
        if (!helper.isFile || !helper.canExecute() || !loader.isFile) {
            throw IOException("Extrator PS4 ARM64 não incluído neste runtime: ${helper.name}")
        }
        val transaction = UUID.randomUUID().toString()
        val input = File(root, ".input-${transaction}.pkg")
        val stage = File(root, ".stage-${transaction}")
        try {
            val reportedSize = activity.contentResolver.query(uri,
                arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
            } ?: -1L
            if (reportedSize > 0 && root.usableSpace < reportedSize + 256L * 1024 * 1024) {
                throw IOException("Espaço insuficiente para copiar o PKG com segurança")
            }
            activity.contentResolver.openInputStream(uri)?.use { source ->
                input.outputStream().buffered(1024 * 1024).use { output ->
                    val buf = ByteArray(1024 * 1024)
                    while (true) {
                        val n = source.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                    }
                }
            } ?: throw IOException("Não foi possível abrir o PKG pelo seletor")
            FileInputStream(input).use { stream ->
                val header = ByteArray(4)
                if (stream.read(header) != 4 || !header.contentEquals(
                    byteArrayOf(0x7f, 0x43, 0x4e, 0x54))) {
                    throw IOException("Arquivo não é um PKG de PS4 (assinatura inválida)")
                }
            }

            val details = execute(loader, helper, listOf("info", input.absolutePath), root)
            val title = TITLE_PATTERN.find(Regex("(?m)^\\s*Title ID:\\s*(CUSA[0-9]{5})\\s*$",
                RegexOption.IGNORE_CASE).find(details)?.groupValues?.get(1) ?: "")
                ?.value?.uppercase()
                ?: throw IOException("PKG não possui TITLE_ID CUSA válido")
            val chosenGame = preferences.getString("game_dir", null)?.let(::File)
            val chosenSfo = chosenGame?.let { File(it, "sce_sys/param.sfo") }
            val chosenTitle = chosenSfo?.takeIf { it.isFile }?.let(ParamSfoTitle::readTitleId)
            if (update) {
                if (chosenGame == null || !File(chosenGame, "eboot.bin").isFile)
                    throw IOException("Instale ou selecione o jogo base antes da atualização")
                if (chosenTitle == null)
                    throw IOException("Não foi possível validar TITLE_ID do jogo base (param.sfo ausente)")
                if (chosenTitle != title)
                    throw IOException("Update ${title} não pertence ao jogo ${chosenTitle}")
            }
            // Guard against accidentally installing another title as this GoW III port.
            if (!title.startsWith("CUSA")) throw IOException("Título não suportado")
            val folderName = if (update) "${title}-update" else title
            val target = File(root, folderName)
            if (!update && target.exists())
                throw IOException("O jogo ${title} já está instalado; não sobrescrevi os dados")

            execute(loader, helper, listOf("extract", input.absolutePath, "--output",
                stage.absolutePath, "--quiet"), root)
            if (!File(stage, "eboot.bin").isFile)
                throw IOException("Extração incompleta: eboot.bin ausente")
            val unpackedTitle = ParamSfoTitle.readTitleId(File(stage, "sce_sys/param.sfo"))
                ?: throw IOException("Extração incompleta: sce_sys/param.sfo inválido")
            if (unpackedTitle != title)
                throw IOException("Título do conteúdo (${unpackedTitle}) difere do cabeçalho (${title})")
            if (!update && !File(stage, "sce_sys").isDirectory)
                throw IOException("Conteúdo do jogo base incompleto")

            // Updates are their own directory. Atomic rename prevents a half-installed
            // patch from ever appearing as selected to game_update.py.
            val backup = File(root, ".old-${transaction}")
            if (target.exists() && !target.renameTo(backup))
                throw IOException("Não foi possível preservar a atualização anterior")
            if (!stage.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target)
                throw IOException("Não foi possível publicar a instalação PKG")
            }
            val prefKey = if (update) "update_dir" else "game_dir"
            val ok = preferences.edit().putString(prefKey, target.absolutePath).commit()
            if (!ok) {
                // Never leave a newly installed update selected on a failed commit.
                if (backup.exists()) {
                    target.renameTo(stage)
                    backup.renameTo(target)
                }
                throw IOException("Falha ao salvar a pasta instalada")
            }
            if (backup.exists()) backup.deleteRecursively()
            return if (update) "Atualização ${title} instalada e selecionada em ${target.path}"
                else "Jogo ${title} instalado e selecionado em ${target.path}"
        } finally {
            input.delete()
            if (stage.exists()) stage.deleteRecursively()
        }
    }

    private fun execute(loader: File, executable: File, args: List<String>,
                        workingDir: File): String {
        // Native tools use a Debian aarch64 PT_INTERP which does not exist at
        // /lib inside Android. Launch the rootfs's dynamic linker explicitly,
        // exactly as run-gow3.sh does for gow3-probe.
        val processBuilder = ProcessBuilder(
            listOf(loader.absolutePath, executable.absolutePath) + args)
            .directory(workingDir)
            .redirectErrorStream(true)
        val files = activity.filesDir
        processBuilder.environment()["LD_LIBRARY_PATH"] =
            File(files, "gow3port/arm64/lib").path + ":" +
            File(files, "rootfs/usr/lib/aarch64-linux-gnu").path + ":" +
            File(files, "rootfs/usr/lib").path
        val process = processBuilder.start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (output.length < 24_000) output.appendLine(line)
            }
        }
        val status = process.waitFor()
        if (status != 0) throw IOException(
            "Falha de extração (${status}): " + output.takeLast(1500))
        return output.toString()
    }
}

/** Reads TITLE_ID from a PARAM.SFO using the PS4 PSF key/data tables. */
internal object ParamSfoTitle {
    fun readTitleId(file: File): String? = runCatching {
        if (!file.isFile || file.length() > 8L * 1024 * 1024 || file.length() < 20) return null
        val bytes = file.readBytes()
        val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (b.int != 0x46535000) return null // "PSF\0" little endian
        b.int // version
        val keys = b.int
        val data = b.int
        val count = b.int
        if (keys < 20 || data < keys || data > bytes.size || count !in 1..1024) return null
        for (i in 0 until count) {
            val entry = 20 + i * 16
            if (entry + 16 > bytes.size) return null
            val name = keys + (b.getShort(entry).toInt() and 0xffff)
            val len = b.getInt(entry + 4)
            val offset = data + b.getInt(entry + 12)
            if (name !in keys until data || len < 0 || offset < data ||
                offset.toLong() + len > bytes.size.toLong()) continue
            val nameEnd = (name until data).firstOrNull { bytes[it] == 0.toByte() } ?: continue
            val key = String(bytes, name, nameEnd - name, Charsets.UTF_8)
            if (key == "TITLE_ID") {
                val text = String(bytes, offset, len, Charsets.UTF_8).trimEnd('\u0000')
                if (Regex("CUSA[0-9]{5}").matches(text)) return text.uppercase()
            }
        }
        null
    }.getOrNull()
}
