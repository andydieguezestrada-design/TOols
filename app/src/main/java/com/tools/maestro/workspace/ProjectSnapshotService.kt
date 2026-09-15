package com.tools.maestro.workspace

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Lightweight transactional safety net for AI/file operations.
 * Snapshots live outside the project so an AI change cannot delete its own backup.
 */
object ProjectSnapshotService {
    private const val MAX_SNAPSHOTS = 12

    fun create(root: File, snapshotsRoot: File, reason: String): File {
        require(root.isDirectory) { "El proyecto no existe." }
        snapshotsRoot.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val safeReason = reason.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(40).ifBlank { "change" }
        val out = File(snapshotsRoot, "${stamp}_$safeReason.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            ProjectService.listFiles(root).forEach { file ->
                val rel = ProjectService.safeRelative(root, file)
                zos.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        prune(snapshotsRoot)
        return out
    }

    fun restore(snapshot: File, root: File) {
        require(snapshot.isFile) { "Snapshot no encontrado." }
        root.mkdirs()
        ZipInputStream(snapshot.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                require(!entry.isDirectory) { "Snapshot inválido." }
                val target = com.tools.maestro.core.security.PathGuard.resolve(root, entry.name)
                target.parentFile?.mkdirs()
                target.outputStream().use { zis.copyTo(it) }
                entry = zis.nextEntry
            }
        }
    }

    fun list(snapshotsRoot: File): List<File> =
        snapshotsRoot.listFiles()?.filter { it.isFile && it.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    private fun prune(root: File) {
        val all = list(root)
        all.drop(MAX_SNAPSHOTS).forEach { it.delete() }
    }
}
