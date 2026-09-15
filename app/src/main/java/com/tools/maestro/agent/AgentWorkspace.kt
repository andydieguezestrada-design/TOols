package com.tools.maestro.agent

import android.content.Context
import com.tools.maestro.core.security.PathGuard
import com.tools.maestro.workspace.ProjectService
import java.io.File

/** Deterministic project tools used by the AI agent and by future UI automation. */
class AgentWorkspace(private val context: Context, private val projectId: Int) {
    private val root: File get() = ProjectService.root(context, projectId)

    fun read(path: String): String {
        val file = PathGuard.resolve(root, path)
        require(file.isFile) { "Archivo no encontrado: $path" }
        require(file.length() <= 2_000_000) { "Archivo demasiado grande para el agente: $path" }
        return file.readText()
    }

    fun list(): List<String> = ProjectService.listFiles(root).map { ProjectService.safeRelative(root, it) }.sorted()

    fun search(query: String): List<String> {
        val q = query.lowercase()
        return ProjectService.listFiles(root).filter { file ->
            file.length() <= 500_000 && runCatching { file.readText().contains(q, true) }.getOrDefault(false)
        }.map { ProjectService.safeRelative(root, it) }
    }

    fun snapshotMap(paths: Collection<String>): Map<String, String> = paths.associateWith { path ->
        val file = PathGuard.resolve(root, path)
        if (file.isFile) file.readText() else ""
    }
}
