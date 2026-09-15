package com.tools.maestro.agent

import android.content.Context
import com.tools.maestro.ai.provider.AIProviderManager
import com.tools.maestro.core.fs.SafeCommandExecutor
import com.tools.maestro.core.fs.DeviceFileManager
import com.tools.maestro.core.security.PathGuard
import com.tools.maestro.workspace.ProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main TOols agent loop. It plans, observes, executes safe tools, verifies results and
 * asks the model for the next step. Destructive actions and file patches never bypass review.
 */
class AgentEngine(private val context: Context) {
    data class Progress(
        val stage: String,
        val detail: String = "",
        val round: Int = 0
    )

    data class Result(
        val answer: String,
        val toolResults: List<String> = emptyList(),
        val pendingApprovals: List<DeviceAgent.ToolCall> = emptyList(),
        val plan: AgentPlan? = null,
        val rounds: Int = 0,
        val elapsedMs: Long = 0
    )

    private val device = DeviceAgent(DeviceFileManager())
    private val shell = SafeCommandExecutor(context)
    private val maxRounds = 6

    suspend fun run(
        userRequest: String,
        projectId: Int?,
        attachments: List<AIProviderManager.Attachment> = emptyList(),
        projectContext: String = "",
        onProgress: (Progress) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val ai = AIProviderManager(context)
        val memoryStore = AgentMemoryStore(context)
        val contextIntelligence = AgentContextIntelligence(context)
        val projectIntelligence = ProjectIntelligenceStore(context)
        val previousMemory = memoryStore.load(projectId)
        var answer = ""
        val results = mutableListOf<String>()
        val pending = mutableListOf<DeviceAgent.ToolCall>()
        var plan: AgentPlan? = null
        var roundsUsed = 0
        fun progress(stage: String, detail: String = "", round: Int = roundsUsed) {
            runCatching { onProgress(Progress(stage, detail, round)) }
        }
        progress("Preparando", "Analizando la solicitud + memoria + RAG")
        val intelligentContext = runCatching { contextIntelligence.prepare(projectId, userRequest) }.getOrDefault("")

        val system = """
Eres TOols AGENT, un agente operativo competente. Tu objetivo es completar la tarea del usuario, no solo explicar cómo hacerla.
CI (Context Intelligence): sintetiza memoria, proyecto, dependencias, historial, herramientas y RAG.
CA (Context Awareness): detecta cambios, permisos, archivos reales, estado de compilación, errores y contradicciones antes de actuar.
RAG: recupera evidencia relevante del índice local; nunca trates un fragmento recuperado como verdad si el archivo actual contradice ese fragmento.
Cuando el motor local sea el proveedor activo, prioriza respuestas estructuradas, análisis de código, JSON válido, tool calls precisas y uso económico del contexto.
Trabaja con este ciclo: entender objetivo -> inspeccionar/recopilar evidencia -> planificar -> ejecutar herramientas seguras -> verificar -> corregir -> informar.
No inventes archivos, resultados, rutas, comandos ni éxitos. Si falta información, usa una herramienta de inspección antes de asumir.
La memoria persistente que aparece abajo sirve para continuar el trabajo después de cerrar la app. Úsala como contexto, pero verifica siempre el estado real del proyecto antes de actuar.
Puedes trabajar sobre el proyecto actual y, si tiene sentido, sobre almacenamiento compartido del teléfono.
Nunca borres, muevas, sobrescribas, ni ejecutes comandos peligrosos sin que TOols pida aprobación explícita.
Para modificar archivos del proyecto, devuelve bloques completos con === FILE: ruta === y ```text ... ```; TOols los convierte en un plan revisable antes de escribir.
Para herramientas usa una línea JSON EXACTA: TOOL_CALL {"action":"...",...}
Acciones de proyecto: project_list, project_read, project_search, project_analyze.
Acciones de conocimiento: rag_search, rag_reindex.
Acción de motor local: local_status.
Acciones de dispositivo: list, tree, search, search_content, inspect, read, mkdir, write, rename, copy, move, delete.
Acción de terminal: run_command con {"command":"..."}. Solo usa terminal si realmente aporta valor.
Puedes emitir varias TOOL_CALL. Después de recibir resultados, continúa razonando y verifica el objetivo. Máximo $maxRounds rondas.
Si una operación no puede hacerse por restricciones de Android, dilo claramente y ofrece la alternativa compatible.
$projectContext

${memoryStore.describe(previousMemory)}

$intelligentContext
""".trimIndent()

        var prompt = "$system\n\nSOLICITUD DEL USUARIO:\n$userRequest"
        memoryStore.saveCheckpoint(
            projectId = projectId, userRequest = userRequest, answer = "",
            stage = "Preparando", detail = "Memoria cargada", round = 0,
            attachmentNames = attachments.map { it.name }
        )
        for (round in 0 until maxRounds) {
            roundsUsed = round + 1
            progress("Razonando", "Ronda $roundsUsed/$maxRounds · preparando contexto", roundsUsed)
            val response = runCatching {
                ai.sendMessageWithAttachments(context, prompt, attachments) { detail ->
                    progress("Conectividad IA", detail, roundsUsed)
                }
            }.getOrElse {
                progress("Error", "Proveedor IA: ${it.message}", roundsUsed)
                "Error del proveedor IA: ${it.message}"
            }
            answer = response

            val calls = device.extract(response)
            progress("Analizando respuesta", "${calls.size} acción(es) detectada(s)", roundsUsed)
            val safeCalls = calls.filterNot { device.requiresApproval(it) }
            val dangerousCalls = calls.filter { device.requiresApproval(it) }
            if (dangerousCalls.isNotEmpty()) pending += dangerousCalls.distinctBy { it.args.toString() }

            val roundResults = mutableListOf<String>()
            for (call in safeCalls) {
                progress("Ejecutando", "${call.action}", roundsUsed)
                val r = executeTool(call, projectId)
                roundResults += "${call.action}: ${r}"
            }
            results += roundResults

            val root = projectId?.let { ProjectService.root(context, it) }
            if (root != null) {
                val files = ProjectService.parseAiFiles(response)
                if (files.isNotEmpty()) {
                    plan = AgentPlanner.fromAiResponse(root, files)
                    roundResults += "FILE_PLAN: ${plan!!.summary}"
                }
            }

            runCatching { contextIntelligence.reindex(projectId) }
            val touchedFiles = plan?.changes?.map { it.path }.orEmpty() + results.mapNotNull { line ->
                Regex("(?:Archivo (?:escrito|creado)|FILE_PLAN):\\s*([^\\n]+)").find(line)?.groupValues?.getOrNull(1)
            }
            val nextActions = buildList {
                if (pending.isNotEmpty()) add("Revisar y aprobar ${pending.size} acción(es) pendiente(s)")
                if (plan?.changes?.isNotEmpty() == true) add("Aplicar el plan revisado y verificar el build")
                if (calls.isNotEmpty()) add("Continuar la tarea desde la última ronda validando el estado real")
            }
            val blockers = results.filter { it.contains("error", true) || it.contains("bloque", true) || it.contains("fall", true) }.takeLast(10)
            memoryStore.saveCheckpoint(
                projectId = projectId, userRequest = userRequest, answer = response,
                stage = "Ronda $roundsUsed", detail = "Checkpoint guardado tras herramientas", round = roundsUsed,
                toolResults = results,
                pendingApprovals = pending.map { "${it.action} ${it.args}" },
                planSummary = plan?.summary.orEmpty(),
                attachmentNames = attachments.map { it.name },
                touchedFiles = touchedFiles,
                nextActions = nextActions,
                decisions = listOf("No asumir el estado del proyecto; verificar archivos reales antes de continuar"),
                blockers = blockers
            )

            runCatching {
                projectId?.let { id ->
                    val root = ProjectService.root(context, id)
                    if (root.isDirectory) projectIntelligence.scan(id, root, userRequest, nextActions, blockers)
                }
            }

            if (calls.isEmpty()) {
                progress("Finalizando", "Respuesta lista", roundsUsed)
                break
            }
            if (roundResults.isEmpty() && dangerousCalls.isEmpty()) break
            if (dangerousCalls.isNotEmpty()) break
            val liveContext = runCatching { contextIntelligence.prepare(projectId, userRequest + "\n" + roundResults.joinToString(" ")) }.getOrDefault("")
            prompt = """
$system

CONTEXTO DE LA TAREA ORIGINAL:
$userRequest

RESPUESTA ANTERIOR DEL AGENTE:
$response

RESULTADOS REALES DE LAS HERRAMIENTAS:
${roundResults.joinToString("\n")}

CONTEXTO INTELIGENTE ACTUALIZADO:
$liveContext

Continúa desde aquí. Verifica los resultados. Si el objetivo ya está cumplido, responde con un resumen preciso y no hagas más llamadas.
""".trimIndent()
        }
        val elapsed = System.currentTimeMillis() - startedAt
        runCatching { contextIntelligence.reindex(projectId) }
        memoryStore.saveCheckpoint(
            projectId = projectId, userRequest = userRequest, answer = answer,
            stage = "Completado", detail = "Checkpoint final · ${elapsed} ms", round = roundsUsed,
            toolResults = results,
            pendingApprovals = pending.map { "${it.action} ${it.args}" },
            planSummary = plan?.summary.orEmpty(),
            attachmentNames = attachments.map { it.name },
            touchedFiles = plan?.changes?.map { it.path }.orEmpty(),
            nextActions = if (pending.isNotEmpty()) listOf("Revisar aprobaciones pendientes") else emptyList(),
            decisions = listOf("Checkpoint final guardado; reanudar desde el estado real del proyecto"),
            blockers = results.filter { it.contains("error", true) || it.contains("fall", true) }.takeLast(10)
        )
        progress("Completado", "${elapsed} ms · $roundsUsed ronda(s)", roundsUsed)
        Result(answer, results, pending, plan, roundsUsed, elapsed)
    }

    private fun executeTool(call: DeviceAgent.ToolCall, projectId: Int?): String {
        val a = call.args
        return when (call.action.lowercase()) {
            "list", "search", "read", "mkdir", "write", "rename", "copy", "move", "delete" -> device.execute(call).result.message
            "tree", "search_content", "inspect" -> device.execute(call).result.message
            "project_list" -> projectId?.let { ProjectService.listFiles(ProjectService.root(context, it)).take(500).joinToString("\n") { f -> ProjectService.safeRelative(ProjectService.root(context, it), f) } } ?: "No hay proyecto abierto."
            "project_read" -> {
                val id = projectId ?: return "No hay proyecto abierto."
                val path = a.get("path")?.asString ?: return "Falta path."
                val root = ProjectService.root(context, id); val f = PathGuard.resolve(root, path)
                require(f.isFile) { "Archivo no encontrado: $path" }; require(f.length() <= 2_000_000) { "Archivo demasiado grande." }
                f.readText().take(120_000)
            }
            "workspace_scan", "project_diff" -> {
                val id = projectId ?: return "No hay proyecto abierto."
                val root = ProjectService.root(context, id)
                val ledger = ProjectIntelligenceStore(context)
                val report = ledger.diff(id, root)
                buildString {
                    append("ESTADO REAL DEL WORKSPACE\n")
                    append("Nuevos: ${report.added.size}\n")
                    append("Modificados: ${report.modified.size}\n")
                    append("Eliminados: ${report.removed.size}\n")
                    append("Sin cambios: ${report.unchanged}\n")
                    if (report.added.isNotEmpty()) append("+ ${report.added.take(80).joinToString("\n+ ")}\n")
                    if (report.modified.isNotEmpty()) append("~ ${report.modified.take(80).joinToString("\n~ ")}\n")
                    if (report.removed.isNotEmpty()) append("- ${report.removed.take(80).joinToString("\n- ")}\n")
                }.take(18000)
            }
            "resume_project" -> {
                val id = projectId ?: return "No hay proyecto abierto."
                val root = ProjectService.root(context, id)
                ProjectIntelligenceStore(context).briefing(id, root, a.get("goal")?.asString.orEmpty())
            }
            "project_analyze" -> {
                val id = projectId ?: return "No hay proyecto abierto."
                val root = ProjectService.root(context, id)
                val metadata = EnhancedCodeAnalyzer().analyzeProject(root)
                val deps = EnhancedCodeAnalyzer().findDependencies(metadata)
                buildString {
                    append("ANÁLISIS PROFUNDO\n")
                    append("Archivos: ${metadata.size}\n")
                    append("Líneas: ${metadata.values.sumOf { it.lineCount }}\n")
                    append("Lenguajes: ${metadata.values.groupingBy { it.language }.eachCount()}\n")
                    append("Dependencias internas: ${deps.values.sumOf { it.size }}\n")
                    metadata.entries.sortedByDescending { it.value.complexity.cyclomaticComplexity + it.value.complexity.nesting }.take(20).forEach { (path, m) ->
                        append("${path} · CC=${m.complexity.cyclomaticComplexity} nesting=${m.complexity.nesting} classes=${m.classes.size} funcs=${m.functions.size}\n")
                    }
                }.take(24000)
            }
            "rag_search" -> {
                val id = projectId ?: return "No hay proyecto abierto."
                val q = a.get("query")?.asString.orEmpty()
                require(q.isNotBlank()) { "Falta query." }
                AgentKnowledgeStore(context).buildContext(id, q, limit = 16, maxChars = 32000)
            }
            "rag_reindex" -> {
                val id = projectId ?: return "No hay proyecto abierto."
                AgentKnowledgeStore(context).indexProject(id, ProjectService.root(context, id))
                "Índice RAG reconstruido."
            }
            "local_status" -> {
                val health = LocalModelController(context).health()
                "Motor local: ${if (health.online) "ONLINE" else "OFFLINE"}; modelos=${health.models.joinToString(", ")}; ${health.message}"
            }
            "project_search" -> {
                val id = projectId ?: return "No hay proyecto abierto."
                val q = a.get("query")?.asString.orEmpty(); require(q.isNotBlank()) { "Consulta vacía." }
                val root = ProjectService.root(context, id)
                ProjectService.listFiles(root).filter { it.length() <= 500_000 && runCatching { it.readText().contains(q, true) }.getOrDefault(false) }
                    .take(500).joinToString("\n") { ProjectService.safeRelative(root, it) }.ifBlank { "Sin coincidencias." }
            }
            "run_command" -> {
                val id = projectId ?: return "No hay proyecto abierto para ejecutar comandos."
                val command = a.get("command")?.asString.orEmpty(); require(command.isNotBlank()) { "Comando vacío." }
                shell.execute(id, command, approved = false).output
            }
            else -> "Acción no reconocida: ${call.action}"
        }
    }
}
