package com.tools.maestro.core.fs

import android.os.Environment
import java.io.File
import java.nio.charset.StandardCharsets

/** Device-level file operations used by TOols AI agent. Paths are relative to shared storage. */
class DeviceFileManager {
    private val root: File = Environment.getExternalStorageDirectory().canonicalFile

    data class Result(val ok: Boolean, val message: String)

    fun hasFullAccess(): Boolean = Environment.isExternalStorageManager()

    private fun resolve(relative: String): File {
        val clean = relative.trim().removePrefix("/")
        val f = File(root, clean).canonicalFile
        require(f.path == root.path || f.path.startsWith(root.path + File.separator)) { "Ruta fuera del almacenamiento compartido." }
        return f
    }

    fun list(path: String = ""): Result = runCatching {
        val dir = resolve(path)
        require(dir.isDirectory) { "No es una carpeta: $path" }
        val entries = dir.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }) ?: emptyList()
        Result(true, entries.take(500).joinToString("\n") { f ->
            val rel = f.relativeTo(root).path
            if (f.isDirectory) "[DIR] $rel" else "[FILE] $rel (${f.length()} bytes)"
        }.ifBlank { "Carpeta vacía." })
    }.getOrElse { Result(false, "LIST error: ${it.message}") }

    fun search(query: String, path: String = ""): Result = runCatching {
        require(query.isNotBlank()) { "Consulta vacía." }
        val base = resolve(path)
        require(base.isDirectory) { "No es una carpeta: $path" }
        val out = mutableListOf<String>()
        base.walkTopDown().onEnter { out.size < 500 }.forEach { f ->
            if (f.name.contains(query, ignoreCase = true)) out += f.relativeTo(root).path
            if (out.size >= 500) return@forEach
        }
        Result(true, out.joinToString("\n").ifBlank { "Sin coincidencias." })
    }.getOrElse { Result(false, "SEARCH error: ${it.message}") }

    fun read(path: String): Result = runCatching {
        val f = resolve(path); require(f.isFile) { "No es un archivo: $path" }; require(f.length() <= 2_000_000) { "Archivo demasiado grande para lectura directa." }
        Result(true, f.readText(StandardCharsets.UTF_8).take(120_000))
    }.getOrElse { Result(false, "READ error: ${it.message}") }

    fun tree(path: String = "", depth: Int = 3): Result = runCatching {
        val dir = resolve(path); require(dir.isDirectory) { "No es una carpeta: $path" }
        val out = StringBuilder()
        fun visit(f: File, level: Int) {
            if (level > depth || out.length > 60000) return
            val prefix = "  ".repeat(level)
            out.append(prefix).append(if (f.isDirectory) "[DIR] " else "[FILE] ").append(f.name)
            if (f.isFile) out.append(" · ").append(f.length()).append(" bytes")
            out.append('\n')
            if (f.isDirectory) f.listFiles()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })?.forEach { visit(it, level + 1) }
        }
        visit(dir, 0)
        Result(true, out.toString().trimEnd())
    }.getOrElse { Result(false, "TREE error: ${it.message}") }

    fun searchContent(query: String, path: String = ""): Result = runCatching {
        require(query.isNotBlank()) { "Consulta vacía." }
        val base = resolve(path); require(base.isDirectory) { "No es una carpeta: $path" }
        val hits = mutableListOf<String>()
        base.walkTopDown().onEnter { hits.size < 300 }.forEach { f ->
            if (f.isFile && f.length() <= 1_500_000L && !f.extension.lowercase().inBinaryExtensions()) {
                val text = runCatching { f.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return@forEach
                if (text.contains(query, true)) {
                    val line = text.lineSequence().firstOrNull { it.contains(query, true) }?.trim().orEmpty()
                    hits += "${f.relativeTo(root).path}: ${line.take(240)}"
                }
            }
        }
        Result(true, hits.joinToString("\n").ifBlank { "Sin coincidencias de contenido." })
    }.getOrElse { Result(false, "CONTENT_SEARCH error: ${it.message}") }

    fun inspect(path: String): Result = runCatching {
        val f = resolve(path); require(f.exists()) { "No existe: $path" }
        val type = when {
            f.isDirectory -> "directory"
            f.extension.lowercase() in setOf("kt","java","kts","gradle","py","js","ts","tsx","jsx") -> "source"
            f.extension.lowercase() in setOf("json","xml","yaml","yml","toml","properties","md","txt","csv") -> "text"
            else -> "binary/unknown"
        }
        val hash = if (f.isFile && f.length() <= 10_000_000L) java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) } else "skipped"
        Result(true, "path=${f.relativeTo(root).path}\ntype=$type\nsize=${if (f.isFile) f.length() else 0}\nmodified=${f.lastModified()}\nhash=$hash\nreadable=${f.canRead()}\nwritable=${f.canWrite()}")
    }.getOrElse { Result(false, "INSPECT error: ${it.message}") }

    private fun String.inBinaryExtensions(): Boolean = lowercase() in setOf("png","jpg","jpeg","gif","webp","mp4","mp3","zip","apk","aab","jar","class","so","db","sqlite","pdf")

    fun mkdir(path: String): Result = runCatching { val f = resolve(path); require(f.mkdirs() || f.isDirectory) { "No se pudo crear la carpeta." }; Result(true, "Carpeta creada: ${f.relativeTo(root).path}") }.getOrElse { Result(false, "MKDIR error: ${it.message}") }

    fun write(path: String, content: String): Result = runCatching { val f = resolve(path); f.parentFile?.mkdirs(); f.writeText(content, StandardCharsets.UTF_8); Result(true, "Archivo escrito: ${f.relativeTo(root).path}") }.getOrElse { Result(false, "WRITE error: ${it.message}") }

    fun rename(path: String, newName: String): Result = runCatching { val f = resolve(path); require(newName.isNotBlank() && !newName.contains('/') && !newName.contains('\\')) { "Nombre inválido." }; val dest = File(f.parentFile, newName).canonicalFile; require(dest.path.startsWith(root.path + File.separator)) { "Destino inválido." }; require(f.renameTo(dest)) { "No se pudo renombrar." }; Result(true, "Renombrado a ${dest.relativeTo(root).path}") }.getOrElse { Result(false, "RENAME error: ${it.message}") }

    fun copy(path: String, destination: String): Result = runCatching {
        val src = resolve(path); val dst = resolve(destination); require(src.exists()) { "Origen inexistente." }
        if (src.isDirectory) copyDir(src, dst) else { dst.parentFile?.mkdirs(); src.copyTo(dst, overwrite = true) }
        Result(true, "Copiado a ${dst.relativeTo(root).path}")
    }.getOrElse { Result(false, "COPY error: ${it.message}") }

    fun move(path: String, destination: String): Result = runCatching { val src = resolve(path); val dst = resolve(destination); require(src.exists()) { "Origen inexistente." }; dst.parentFile?.mkdirs(); require(src.renameTo(dst)) { "No se pudo mover; usa COPY si cruza volúmenes." }; Result(true, "Movido a ${dst.relativeTo(root).path}") }.getOrElse { Result(false, "MOVE error: ${it.message}") }

    fun delete(path: String, recursive: Boolean = false): Result = runCatching { val f = resolve(path); require(f.exists()) { "No existe: $path" }; if (f.isDirectory && !recursive && f.listFiles()?.isNotEmpty() == true) error("Carpeta no vacía; requiere recursive=true."); if (f.isDirectory) f.deleteRecursively() else require(f.delete()) { "No se pudo borrar." }; Result(true, "Eliminado: $path") }.getOrElse { Result(false, "DELETE error: ${it.message}") }

    private fun copyDir(src: File, dst: File) { if (!dst.exists()) dst.mkdirs(); src.listFiles()?.forEach { child -> val target = File(dst, child.name); if (child.isDirectory) copyDir(child, target) else child.copyTo(target, overwrite = true) } }
}
