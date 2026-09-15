package com.tools.maestro.agent

import android.content.Context
import com.google.gson.GsonBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent checkpoint memory for the TOols agent.
 *
 * Memory is stored per project (and one global slot when no project is open),
 * so closing the app does not erase the agent's last known state.
 * Only task/checkpoint text and attachment names are stored; attachment bytes
 * are never copied into memory.
 */
class AgentMemoryStore(context: Context) {

    data class Interaction(
        val timestamp: Long,
        val userRequest: String,
        val answer: String,
        val toolResults: List<String> = emptyList()
    )

    data class Snapshot(
        val version: Int = 2,
        val projectId: Int? = null,
        val updatedAt: Long = System.currentTimeMillis(),
        val lastUserRequest: String = "",
        val lastAnswer: String = "",
        val lastStage: String = "",
        val lastDetail: String = "",
        val lastRound: Int = 0,
        val pendingApprovals: List<String> = emptyList(),
        val planSummary: String = "",
        val attachmentNames: List<String> = emptyList(),
        val recentInteractions: List<Interaction> = emptyList(),
        val touchedFiles: List<String> = emptyList(),
        val nextActions: List<String> = emptyList(),
        val decisions: List<String> = emptyList(),
        val blockers: List<String> = emptyList()
    )

    private val root = File(context.filesDir, "agent_memory").apply { mkdirs() }
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val writeSequence = AtomicLong(0)

    fun load(projectId: Int?): Snapshot? = synchronized(this) {
        val file = memoryFile(projectId)
        if (!file.isFile) return null
        runCatching { gson.fromJson(file.readText(Charsets.UTF_8), Snapshot::class.java) }.getOrNull()
    }

    fun saveCheckpoint(
        projectId: Int?,
        userRequest: String,
        answer: String,
        stage: String,
        detail: String,
        round: Int,
        toolResults: List<String> = emptyList(),
        pendingApprovals: List<String> = emptyList(),
        planSummary: String = "",
        attachmentNames: List<String> = emptyList(),
        touchedFiles: List<String> = emptyList(),
        nextActions: List<String> = emptyList(),
        decisions: List<String> = emptyList(),
        blockers: List<String> = emptyList()
    ) = synchronized(this) {
        val previous = load(projectId)
        val interaction = if (userRequest.isNotBlank() || answer.isNotBlank()) {
            Interaction(System.currentTimeMillis(), userRequest.take(8000), answer.take(12000), toolResults.takeLast(12).map { it.take(3000) })
        } else null
        val interactions = ((previous?.recentInteractions ?: emptyList()) + listOfNotNull(interaction)).takeLast(8)
        val snapshot = Snapshot(
            projectId = projectId,
            updatedAt = System.currentTimeMillis(),
            lastUserRequest = userRequest.take(12000),
            lastAnswer = answer.take(16000),
            lastStage = stage.take(300),
            lastDetail = detail.take(1000),
            lastRound = round,
            pendingApprovals = pendingApprovals.take(20).map { it.take(2000) },
            planSummary = planSummary.take(5000),
            attachmentNames = attachmentNames.take(30).map { it.take(300) },
            recentInteractions = interactions,
            touchedFiles = touchedFiles.distinct().takeLast(100).map { it.take(500) },
            nextActions = nextActions.take(30).map { it.take(1200) },
            decisions = decisions.takeLast(30).map { it.take(1200) },
            blockers = blockers.takeLast(20).map { it.take(1200) }
        )
        atomicWrite(memoryFile(projectId), gson.toJson(snapshot))
    }

    fun clear(projectId: Int?) = synchronized(this) {
        runCatching { memoryFile(projectId).delete() }
    }

    fun describe(snapshot: Snapshot?): String {
        if (snapshot == null) return "No existe memoria previa para este proyecto."
        return buildString {
            append("MEMORIA PERSISTENTE DE TOOLS (checkpoint anterior)\n")
            append("Última actualización: ").append(snapshot.updatedAt).append('\n')
            if (snapshot.lastUserRequest.isNotBlank()) append("Última solicitud: ").append(snapshot.lastUserRequest).append('\n')
            if (snapshot.lastAnswer.isNotBlank()) append("Última respuesta/resumen: ").append(snapshot.lastAnswer).append('\n')
            if (snapshot.lastStage.isNotBlank()) append("Última etapa: ").append(snapshot.lastStage).append(" — ").append(snapshot.lastDetail).append('\n')
            if (snapshot.lastRound > 0) append("Última ronda: ").append(snapshot.lastRound).append('\n')
            if (snapshot.planSummary.isNotBlank()) append("Plan pendiente/anterior: ").append(snapshot.planSummary).append('\n')
            if (snapshot.pendingApprovals.isNotEmpty()) append("Aprobaciones pendientes: ").append(snapshot.pendingApprovals.joinToString(" | ")).append('\n')
            if (snapshot.attachmentNames.isNotEmpty()) append("Adjuntos de la última tarea: ").append(snapshot.attachmentNames.joinToString(", ")).append('\n')
            if (snapshot.touchedFiles.isNotEmpty()) append("Archivos tocados: ").append(snapshot.touchedFiles.joinToString(" | ")).append('\n')
            if (snapshot.nextActions.isNotEmpty()) append("Siguientes acciones: ").append(snapshot.nextActions.joinToString(" | ")).append('\n')
            if (snapshot.decisions.isNotEmpty()) append("Decisiones conservadas: ").append(snapshot.decisions.joinToString(" | ")).append('\n')
            if (snapshot.blockers.isNotEmpty()) append("Bloqueos pendientes: ").append(snapshot.blockers.joinToString(" | ")).append('\n')
            if (snapshot.recentInteractions.isNotEmpty()) {
                append("Historial reciente:\n")
                snapshot.recentInteractions.takeLast(6).forEachIndexed { index, item ->
                    append("${index + 1}. Usuario: ${item.userRequest.take(1800)}\n")
                    append("   TOols: ${item.answer.take(2200)}\n")
                    if (item.toolResults.isNotEmpty()) append("   Resultados: ${item.toolResults.joinToString(" | ").take(2500)}\n")
                }
            }
            append("Regla: usa esta memoria como continuidad, pero verifica siempre el estado real de los archivos antes de modificar o afirmar algo.")
        }.take(30000)
    }

    fun hasMemory(projectId: Int?): Boolean = memoryFile(projectId).isFile

    private fun memoryFile(projectId: Int?): File {
        val key = projectId?.toString() ?: "global"
        return File(root, "$key.json")
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.${writeSequence.incrementAndGet()}.tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IllegalStateException("No se pudo guardar la memoria del agente")
        }
    }
}
