package com.tools.maestro.agent

import android.content.Context
import com.google.gson.GsonBuilder
import com.tools.maestro.workspace.ProjectService
import java.io.File
import java.security.MessageDigest

/**
 * Durable project ledger: remembers the state that was actually observed, not only chat text.
 * It lets the agent detect changes made while TOols was closed and build a reliable resume briefing.
 */
class ProjectIntelligenceStore(context: Context) {
    data class FileState(val path: String, val size: Long, val modified: Long, val sha256: String)
    data class Ledger(
        val version: Int = 1,
        val projectId: Int? = null,
        val updatedAt: Long = 0L,
        val projectFingerprint: String = "",
        val files: List<FileState> = emptyList(),
        val lastGoal: String = "",
        val lastKnownNextSteps: List<String> = emptyList(),
        val lastKnownBlockers: List<String> = emptyList()
    )
    data class ChangeReport(val added: List<String>, val modified: List<String>, val removed: List<String>, val unchanged: Int)

    private val root = File(context.filesDir, "project_intelligence").apply { mkdirs() }
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun load(projectId: Int?): Ledger? = runCatching {
        val f = file(projectId)
        if (!f.isFile) null else gson.fromJson(f.readText(Charsets.UTF_8), Ledger::class.java)
    }.getOrNull()

    fun scan(projectId: Int?, projectRoot: File, goal: String = "", nextSteps: List<String> = emptyList(), blockers: List<String> = emptyList()): Pair<Ledger, ChangeReport> {
        require(projectRoot.isDirectory) { "Proyecto no encontrado." }
        val previous = load(projectId)
        val states = ProjectService.listFiles(projectRoot)
            .filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }
            .filter { it.length() <= 5_000_000L }
            .take(20_000)
            .mapNotNull { f ->
                val rel = runCatching { ProjectService.safeRelative(projectRoot, f) }.getOrNull() ?: return@mapNotNull null
                val hash = runCatching { sha256(f) }.getOrNull() ?: return@mapNotNull null
                FileState(rel, f.length(), f.lastModified(), hash)
            }
            .sortedBy { it.path }
        val old = previous?.files?.associateBy { it.path }.orEmpty()
        val now = states.associateBy { it.path }
        val added = now.keys.filter { it !in old }
        val removed = old.keys.filter { it !in now }
        val modified = now.keys.filter { it in old && old.getValue(it).sha256 != now.getValue(it).sha256 }
        val report = ChangeReport(added, modified, removed, states.size - added.size - modified.size)
        val fingerprint = sha256(states.joinToString("\n") { "${it.path}|${it.size}|${it.sha256}" }.toByteArray())
        val ledger = Ledger(1, projectId, System.currentTimeMillis(), fingerprint, states,
            goal.take(8000), nextSteps.take(30).map { it.take(1200) }, blockers.take(20).map { it.take(1200) })
        atomicWrite(projectId, gson.toJson(ledger))
        return ledger to report
    }

    fun briefing(projectId: Int?, projectRoot: File?, goal: String = ""): String {
        if (projectRoot?.isDirectory != true) return "CONTINUIDAD: no hay proyecto local abierto."
        val old = load(projectId) ?: return "CONTINUIDAD: primera inspección persistente de este proyecto; todavía no existe un estado anterior."
        val (_, changes) = scan(projectId, projectRoot, goal, old.lastKnownNextSteps, old.lastKnownBlockers)
        return buildString {
            append("CONTINUIDAD DEL PROYECTO\n")
            append("Estado anterior: ${old.updatedAt}\n")
            append("Archivos observados anteriormente: ${old.files.size}\n")
            append("Cambios desde la última sesión: +${changes.added.size} nuevos, ~${changes.modified.size} modificados, -${changes.removed.size} eliminados, =${changes.unchanged} sin cambios.\n")
            if (old.lastGoal.isNotBlank()) append("Último objetivo: ${old.lastGoal}\n")
            if (old.lastKnownNextSteps.isNotEmpty()) append("Próximos pasos recordados: ${old.lastKnownNextSteps.joinToString(" | ")}\n")
            if (old.lastKnownBlockers.isNotEmpty()) append("Bloqueos recordados: ${old.lastKnownBlockers.joinToString(" | ")}\n")
            if (changes.added.isNotEmpty()) append("Nuevos: ${changes.added.take(20).joinToString(", ")}\n")
            if (changes.modified.isNotEmpty()) append("Modificados: ${changes.modified.take(30).joinToString(", ")}\n")
            if (changes.removed.isNotEmpty()) append("Eliminados: ${changes.removed.take(30).joinToString(", ")}\n")
            append("Regla: si hubo cambios externos, vuelve a leer los archivos afectados antes de editar. La memoria no sustituye al estado real.")
        }.take(18000)
    }

    fun diff(projectId: Int?, projectRoot: File): ChangeReport {
        val old = load(projectId) ?: return ChangeReport(emptyList(), emptyList(), emptyList(), 0)
        val (_, report) = scan(projectId, projectRoot, old.lastGoal, old.lastKnownNextSteps, old.lastKnownBlockers)
        return report
    }

    private fun file(projectId: Int?) = File(root, "${projectId ?: "global"}.json")
    private fun sha256(file: File): String = sha256(file.readBytes())
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun atomicWrite(projectId: Int?, text: String) {
        val target = file(projectId)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) { target.delete(); require(tmp.renameTo(target)) { "No se pudo guardar el ledger del proyecto" } }
    }
}
