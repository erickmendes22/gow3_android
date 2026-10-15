package com.gow3.android.data

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct, zero-copy PKG installation.
 *
 * Select an existing local PKG, then choose the EXACT empty destination folder.
 * orbis-pkg-util opens the original local file and creates the unpacked files
 * inside that folder. The PKG is never duplicated; there is no staging folder
 * and the output is never copied between folders.
 *
 * This native extractor currently requires a directly addressable filesystem
 * path for both the PKG and destination. A remote SAF/cloud provider cannot
 * work with its seek/mmap I/O; reject it instead of making an implicit copy.
 *
 * GoW3's existing game_update.py overlays the independent update folder on the
 * independent base folder. No updated files are duplicated into the base.
 */
internal class PkgInstallManager(
    private val activity: Activity,
    private val onState: (busy: Boolean, message: String?) -> Unit,
) {
    companion object {
        const val REQUEST_BASE_PKG = 1008
        const val REQUEST_UPDATE_PKG = 1009
        const val REQUEST_BASE_DIRECTORY = 1010
        const val REQUEST_UPDATE_DIRECTORY = 1011
        const val REQUEST_ALL_FILES_ACCESS = 1012
        private val TITLE_PATTERN = Regex("^CUSA[0-9]{5}$", RegexOption.IGNORE_CASE)
    }

    private data class Selection(val pkg: Uri, val update: Boolean)
    private var selected: Selection? = null
    private var selectedDestination: Uri? = null
    private val installing = AtomicBoolean(false)
    private val worker = Executors.newSingleThreadExecutor()
    private val preferences get() = activity.getSharedPreferences("gow3", Activity.MODE_PRIVATE)

    fun pickBasePkg() = pick(false)
    fun pickUpdatePkg() = pick(true)

    private fun pick(update: Boolean) {
        if (installing.get() || selected != null) {
            Toast.makeText(activity, "Finalize a seleção anterior antes de instalar outro PKG",
                Toast.LENGTH_SHORT).show()
            return
        }
        activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, if (update) REQUEST_UPDATE_PKG else REQUEST_BASE_PKG)
    }

    fun onPickerResult(request: Int, result: Int, data: Intent?): Boolean {
        when (request) {
            REQUEST_BASE_PKG, REQUEST_UPDATE_PKG -> {
                if (result != Activity.RESULT_OK || data?.data == null) return true
                val update = request == REQUEST_UPDATE_PKG
                selected = Selection(data.data!!, update)
                try {
                    activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    }, if (update) REQUEST_UPDATE_DIRECTORY else REQUEST_BASE_DIRECTORY)
                } catch (e: Exception) {
                    selected = null
                    error("Não foi possível abrir o seletor de pastas: ${e.message}")
                }
                return true
            }
            REQUEST_BASE_DIRECTORY, REQUEST_UPDATE_DIRECTORY -> {
                val choice = selected
                if (result != Activity.RESULT_OK || data?.data == null ||
                    choice == null ||
                    (request == REQUEST_UPDATE_DIRECTORY) != choice.update) {
                    selected = null
                    selectedDestination = null
                    return true
                }
                selectedDestination = data.data
                try {
                    activity.contentResolver.takePersistableUriPermission(
                        data.data!!, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // The selection may be a non-persistable provider; we still
                    // validate real write access before extracting anything.
                }
                if (needsAllFilesAccess()) {
                    AlertDialog.Builder(activity)
                        .setTitle("Permissão para instalação direta")
                        .setMessage("Para extrair o PKG diretamente na pasta escolhida, " +
                            "sem criar cópias, o extrator precisa acessar os arquivos " +
                            "locais pelo caminho de armazenamento. Autorize o acesso a " +
                            "todos os arquivos para continuar.")
                        .setPositiveButton("Permitir") { _, _ ->
                            try {
                                activity.startActivityForResult(Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${activity.packageName}")),
                                    REQUEST_ALL_FILES_ACCESS)
                            } catch (e: Exception) {
                                clearSelection()
                                error("Não foi possível solicitar acesso aos arquivos: ${e.message}")
                            }
                        }
                        .setNegativeButton("Cancelar") { _, _ -> clearSelection() }
                        .show()
                } else {
                    startSelectedInstall()
                }
                return true
            }
            REQUEST_ALL_FILES_ACCESS -> {
                if (needsAllFilesAccess()) {
                    clearSelection()
                    error("Sem permissão de armazenamento: instalação direta cancelada, sem cópias")
                } else {
                    startSelectedInstall()
                }
                return true
            }
            else -> return false
        }
    }

    private fun needsAllFilesAccess() =
        android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()

    private fun clearSelection() {
        selected = null
        selectedDestination = null
    }

    private fun startSelectedInstall() {
        val choice = selected ?: return
        val destination = selectedDestination ?: return
        if (!installing.compareAndSet(false, true)) return
        clearSelection()
        onState(true, "Extraindo PKG diretamente na pasta selecionada…")
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (choice.update) "Instalando atualização PKG" else "Instalando jogo PKG")
            .setMessage("A extração usa a pasta escolhida, sem cópia adicional do PKG.")
            .setCancelable(false)
            .create()
        dialog.show()
        worker.execute {
            val result = runCatching { install(choice.pkg, destination, choice.update) }
            activity.runOnUiThread {
                installing.set(false)
                dialog.dismiss()
                val message = result.fold(
                    onSuccess = { it },
                    onFailure = { "PKG não instalado: ${it.message ?: it.javaClass.simpleName}" },
                )
                onState(false, message)
                AlertDialog.Builder(activity)
                    .setTitle(if (result.isSuccess) "PKG instalado" else "Instalação PKG não concluída")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun install(sourceUri: Uri, targetUri: Uri, update: Boolean): String {
        val helper = File(activity.filesDir, "gow3port/arm64/bin/orbis-pkg-util")
        val loader = File(activity.filesDir,
            "rootfs/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
        if (!helper.isFile || !helper.canExecute() || !loader.isFile)
            throw IOException("O runtime não contém o extrator PKG ARM64")

        // Resolve local primary/removable-storage document IDs. Never use
        // OpenInputStream -> temporary PKG; the extractor needs random access.
        val pkg = localDocumentFile(sourceUri, directory = false)
            ?: throw IOException("O PKG precisa estar num armazenamento local acessível. " +
                "Não é possível extrair diretamente um arquivo remoto/cloud sem duplicá-lo.")
        val dest = localDocumentFile(targetUri, directory = true)
            ?: throw IOException("Pasta de destino não é um diretório local acessível")
        if (!pkg.isFile || !pkg.canRead())
            throw IOException("PKG original indisponível: ${pkg.path}")
        if (!dest.isDirectory || !dest.canWrite())
            throw IOException("Sem permissão de escrita na pasta escolhida: ${dest.path}")
        if (pkg.canonicalFile == dest.canonicalFile || pkg.canonicalFile.parentFile == dest.canonicalFile)
            throw IOException("Escolha outra pasta: o PKG não pode ficar dentro do destino")
        if (dest.listFiles()?.isNotEmpty() != false)
            throw IOException("A pasta escolhida precisa estar vazia para evitar arquivos duplicados " +
                "ou sobrescritas. Selecione uma pasta vazia, separada do jogo base.")
        // Do not install an update over the game: our existing runtime overlays
        // the two directories and should never copy the patch into the base.
        val base = preferences.getString("game_dir", null)?.let(::File)
        if (base != null && (base.canonicalFile == dest.canonicalFile ||
                isNested(dest.canonicalFile, base.canonicalFile)))
            throw IOException("Não extraia uma atualização dentro da pasta do jogo base")
        val updateDir = preferences.getString("update_dir", null)?.let(::File)
        if (!update && updateDir != null && updateDir.canonicalFile == dest.canonicalFile)
            throw IOException("A pasta escolhida está registrada como atualização")

        FileInputStream(pkg).use { stream ->
            val magic = ByteArray(4)
            if (stream.read(magic) != 4 ||
                !magic.contentEquals(byteArrayOf(0x7f, 0x43, 0x4e, 0x54)))
                throw IOException("Arquivo não possui assinatura PKG PS4")
        }
        val metadata = execute(loader, helper, listOf("info", pkg.canonicalPath), dest)
        val title = Regex("(?im)^\\s*Title ID:\\s*(CUSA[0-9]{5})\\s*$")
            .find(metadata)?.groupValues?.get(1)?.uppercase()
            ?: throw IOException("TITLE_ID ausente no cabeçalho PKG")
        if (!TITLE_PATTERN.matches(title)) throw IOException("TITLE_ID inválido")
        if (update) {
            if (base == null || !File(base, "eboot.bin").isFile)
                throw IOException("Selecione ou instale o jogo base antes do update")
            val baseTitle = ParamSfoTitle.readTitleId(File(base, "sce_sys/param.sfo"))
                ?: throw IOException("TITLE_ID do jogo base indisponível")
            if (baseTitle != title)
                throw IOException("Atualização ${title} não corresponde ao jogo ${baseTitle}")
        }

        // Native extractor writes DIRECTLY to dest; no stage directory, no
        // second game copy and no second PKG copy. Destination is empty.
        execute(loader, helper, listOf("extract", pkg.canonicalPath, "--output",
            dest.canonicalPath, "--quiet"), dest)

        val extractedTitle = ParamSfoTitle.readTitleId(File(dest, "sce_sys/param.sfo"))
            ?: throw IOException("Extração incompleta: param.sfo ausente/inválido. " +
                "Arquivos incompletos ficaram na pasta escolhida; nenhum foi copiado.")
        if (extractedTitle != title || !File(dest, "eboot.bin").isFile)
            throw IOException("Conteúdo extraído não corresponde ao PKG ou está incompleto")
        val key = if (update) "update_dir" else "game_dir"
        if (!preferences.edit().putString(key, dest.canonicalPath).commit())
            throw IOException("Conteúdo extraído, mas não foi possível selecionar a pasta")
        return if (update)
            "Update ${title} instalado diretamente em ${dest.path}, sem duplicar o jogo."
        else "Jogo ${title} instalado diretamente em ${dest.path}, sem cópias adicionais."
    }

    private fun isNested(dir: File, ancestor: File): Boolean {
        var current: File? = dir
        while (current != null) {
            if (current == ancestor) return true
            current = current.parentFile
        }
        return false
    }

    /**
     * SAF grants do not translate into Linux pathname permissions. We only
     * resolve actual local storage volumes, then check File.canRead/Write.
     * SAF destinations backed by cloud/virtual providers are not supported
     * by the current native pathname-only PKG extractor.
     */
    private fun localDocumentFile(uri: Uri, directory: Boolean): File? {
        return try {
            if (uri.authority != "com.android.externalstorage.documents") return null
            val id = if (directory) DocumentsContract.getTreeDocumentId(uri)
                else DocumentsContract.getDocumentId(uri)
            val colon = id.indexOf(':')
            if (colon <= 0) return null
            val volume = id.substring(0, colon)
            val rel = id.substring(colon + 1)
            val root = if (volume.equals("primary", ignoreCase = true)) {
                Environment.getExternalStorageDirectory()
            } else {
                if (!Regex("[A-Za-z0-9_-]{4,24}").matches(volume)) return null
                File("/storage", volume)
            }
            val output = File(root, rel).canonicalFile
            // Do not let a crafted document ID escape its storage volume.
            if (!isNested(output, root.canonicalFile)) return null
            output
        } catch (_: Exception) {
            null
        }
    }

    private fun execute(loader: File, executable: File, args: List<String>,
                        workingDir: File): String {
        val builder = ProcessBuilder(listOf(loader.absolutePath, executable.absolutePath) + args)
            .directory(workingDir)
            .redirectErrorStream(true)
        val files = activity.filesDir
        builder.environment()["LD_LIBRARY_PATH"] =
            File(files, "gow3port/arm64/lib").path + ":" +
            File(files, "rootfs/usr/lib/aarch64-linux-gnu").path + ":" +
            File(files, "rootfs/usr/lib").path
        val process = builder.start()
        val output = StringBuilder()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (output.length < 24_000) output.appendLine(line)
            }
        }
        val status = process.waitFor()
        if (status != 0)
            throw IOException("Extração falhou (${status}): " + output.takeLast(1600))
        return output.toString()
    }
}

/** Reads the TITLE_ID directly from the PS4 PARAM.SFO key and data tables. */
internal object ParamSfoTitle {
    fun readTitleId(file: File): String? = runCatching {
        if (!file.isFile || file.length() < 20 || file.length() > 8L * 1024 * 1024)
            return null
        val bytes = file.readBytes()
        val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (b.int != 0x46535000) return null
        b.int
        val keys = b.int
        val data = b.int
        val count = b.int
        if (keys < 20 || data < keys || data > bytes.size || count !in 1..1024)
            return null
        for (i in 0 until count) {
            val entry = 20 + i * 16
            if (entry + 16 > bytes.size) return null
            val name = keys + (b.getShort(entry).toInt() and 0xffff)
            val len = b.getInt(entry + 4)
            val offset = data + b.getInt(entry + 12)
            if (name !in keys until data || len < 0 || offset < data ||
                offset.toLong() + len > bytes.size.toLong()) continue
            val nameEnd = (name until data).firstOrNull { bytes[it] == 0.toByte() } ?: continue
            if (String(bytes, name, nameEnd - name, Charsets.UTF_8) == "TITLE_ID") {
                val id = String(bytes, offset, len, Charsets.UTF_8).trimEnd('\u0000')
                if (Regex("CUSA[0-9]{5}").matches(id)) return id.uppercase()
            }
        }
        null
    }.getOrNull()
}
