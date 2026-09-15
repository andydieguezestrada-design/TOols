package com.tools.maestro.agent

import java.io.File

object AgentPlanner {
    fun fromAiResponse(root: File, files: Map<String, String>): AgentPlan {
        val changes = files.map { (path, after) ->
            val file = File(root, path)
            val before = if (file.isFile) runCatching { file.readText() }.getOrDefault("") else ""
            ProposedChange(path, before, after, if (file.exists()) ProposedChange.Kind.MODIFY else ProposedChange.Kind.CREATE)
        }.filter { it.before != it.after }
        return AgentPlan(
            summary = if (changes.isEmpty()) "La IA no propuso cambios de archivos." else "${changes.size} archivo(s) listos para revisión.",
            changes = changes
        )
    }
}
