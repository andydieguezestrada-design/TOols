package com.tools.maestro.agent

import com.tools.maestro.core.security.PathGuard
import java.io.File

/** Applies a previously reviewed change set; validation happens before any write. */
class AgentPatchEngine(private val root: File) {
    fun validate(changes: List<ProposedChange>): List<String> {
        val errors = mutableListOf<String>()
        changes.forEach { c ->
            runCatching { PathGuard.resolve(root, c.path) }
                .onFailure { errors += "${c.path}: ${it.message}" }
        }
        if (changes.map { it.path }.distinct().size != changes.size) errors += "Hay rutas duplicadas en el cambio."
        return errors
    }

    fun apply(changes: List<ProposedChange>) {
        val errors = validate(changes)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        changes.forEach { c ->
            val file = PathGuard.resolve(root, c.path)
            when (c.kind) {
                ProposedChange.Kind.DELETE -> if (file.exists()) require(file.delete()) { "No se pudo borrar ${c.path}" }
                ProposedChange.Kind.CREATE, ProposedChange.Kind.MODIFY -> {
                    file.parentFile?.mkdirs()
                    file.writeText(c.after)
                }
            }
        }
    }
}
