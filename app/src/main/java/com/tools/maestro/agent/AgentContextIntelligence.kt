package com.tools.maestro.agent

import android.content.Context
import com.tools.maestro.ai.provider.AIProviderManager
import com.tools.maestro.workspace.ProjectService
import java.io.File

/** Combines persistent memory, project facts, RAG evidence and live provider state. */
class AgentContextIntelligence(private val context: Context) {
    private val rag = AgentKnowledgeStore(context)
    private val memory = AgentMemoryStore(context)
    private val analyzer = EnhancedCodeAnalyzer()
    private val intelligence = ProjectIntelligenceStore(context)

    fun prepare(projectId: Int?, query: String): String {
        val root = projectId?.let { ProjectService.root(context, it) }
        val memoryText = memory.describe(memory.load(projectId))
        val projectText = if (root?.isDirectory == true) projectFacts(root) else "No hay proyecto local abierto."
        val continuity = if (root?.isDirectory == true) runCatching { intelligence.briefing(projectId, root, query) }.getOrDefault("Continuidad no disponible.") else "Continuidad: sin proyecto."
        val ragText = if (root?.isDirectory == true) {
            runCatching { rag.indexProject(projectId, root) }.onFailure { }.getOrNull()
            rag.buildContext(projectId, query)
        } else "RAG: no hay proyecto indexable."
        val providers = AIProviderManager(context).providerStatus().joinToString(" | ")
        return """
CONTEXTO INTELIGENTE (CI/CA)
$projectText

$continuity

ESTADO DE PROVEEDORES:
$providers

$memoryText

$ragText

REGLA DE CONTEXTO: la evidencia RAG y la memoria son pistas persistentes. El estado actual de archivos, permisos y herramientas tiene prioridad. Si hay contradicción, inspecciona de nuevo.
""".trim().take(50000)
    }

    fun reindex(projectId: Int?) {
        val root = projectId?.let { ProjectService.root(context, it) } ?: return
        if (root.isDirectory) rag.indexProject(projectId, root)
    }

    private fun projectFacts(root: File): String {
        val metadata = runCatching { analyzer.analyzeProject(root) }.getOrDefault(emptyMap())
        val langStats = metadata.values.groupingBy { it.language }.eachCount()
        val files = metadata.size
        val lines = metadata.values.sumOf { it.lineCount }
        val deps = runCatching { analyzer.findDependencies(metadata) }.getOrDefault(emptyMap())
        val hotspots = metadata.entries
            .sortedByDescending { it.value.complexity.cyclomaticComplexity + it.value.complexity.nesting }
            .take(8)
            .joinToString(" | ") { "${it.key}:${it.value.complexity.cyclomaticComplexity}/${it.value.complexity.nesting}" }
        return """
PROYECTO ACTUAL
- Raíz: ${root.absolutePath}
- Archivos analizables: $files
- Líneas aproximadas: $lines
- Lenguajes: ${langStats.entries.joinToString(", ") { "${it.key}=${it.value}" }}
- Relaciones de dependencia detectadas: ${deps.values.sumOf { it.size }}
- Hotspots de complejidad: $hotspots
""".trim()
    }
}
