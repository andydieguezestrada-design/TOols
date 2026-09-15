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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Agente mejorado y competente con capacidades avanzadas de razonamiento,
 * planificación, análisis y validación. Capaz de manejar tareas complejas.
 */
class EnhancedAgentEngine(private val context: Context) {
    
    data class EnhancedResult(
        val answer: String,
        val toolResults: List<String> = emptyList(),
        val pendingApprovals: List<DeviceAgent.ToolCall> = emptyList(),
        val plan: AgentPlan? = null,
        val executionPlan: AdvancedTaskPlanner.ExecutionPlan? = null,
        val validations: List<AdvancedValidationEngine.ChangeValidation> = emptyList(),
        val rounds: Int = 0,
        val executionTime: Long = 0,
        val success: Boolean = false,
        val metadata: ExecutionMetadata = ExecutionMetadata()
    )
    
    data class ExecutionMetadata(
        val tasksCompleted: Int = 0,
        val tasksSkipped: Int = 0,
        val tasksFailed: Int = 0,
        val toolCallsExecuted: Int = 0,
        val fileChanges: Int = 0,
        val validationScore: Float = 0f,
        val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
        val agentVersion: String = "2.0"
    )
    
    private val device = DeviceAgent(DeviceFileManager())
    private val shell = SafeCommandExecutor(context)
    private val analyzer = EnhancedCodeAnalyzer()
    private val planner = AdvancedTaskPlanner(analyzer)
    private val validator = AdvancedValidationEngine()
    
    private val maxRounds = 10
    private val executionLog = mutableListOf<ExecutionLogEntry>()
    
    data class ExecutionLogEntry(
        val round: Int,
        val action: String,
        val result: String,
        val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ISO_TIME)
    )
    
    suspend fun run(
        userRequest: String,
        projectId: Int?,
        attachments: List<AIProviderManager.Attachment> = emptyList(),
        projectContext: String = ""
    ): EnhancedResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var answer = ""
        val results = mutableListOf<String>()
        val pending = mutableListOf<DeviceAgent.ToolCall>()
        var executionPlan: AdvancedTaskPlanner.ExecutionPlan? = null
        var plan: AgentPlan? = null
        val validations = mutableListOf<AdvancedValidationEngine.ChangeValidation>()
        var roundsUsed = 0
        var success = false
        var tasksCompleted = 0
        var toolCallsExecuted = 0
        var fileChanges = 0
        var validationScore = 0f
        
        executionLog.clear()
        
        // FASE 1: Análisis del proyecto
        var projectRoot: File? = null
        var projectMetadata: Map<String, EnhancedCodeAnalyzer.FileMetadata> = emptyMap()
        
        if (projectId != null) {
            try {
                projectRoot = ProjectService.root(context, projectId)
                projectMetadata = analyzer.analyzeProject(projectRoot)
                logExecution(0, "PROJECT_ANALYSIS", "Analizados ${projectMetadata.size} archivos")
            } catch (e: Exception) {
                logExecution(0, "PROJECT_ANALYSIS_ERROR", e.message ?: "Error desconocido")
            }
        }
        
        // FASE 2: Planificación inteligente
        try {
            val planContext = AdvancedTaskPlanner.PlanContext(
                projectRoot = projectRoot,
                projectMetadata = projectMetadata,
                availableTools = listOf(
                    "project_list", "project_read", "project_search",
                    "list", "search", "read", "mkdir", "write", "rename", "copy", "move", "delete",
                    "run_command"
                )
            )
            executionPlan = planner.planUserRequest(userRequest, planContext)
            logExecution(0, "TASK_PLANNING", 
                "${executionPlan.tasks.size} tareas creadas con complejidad: ${executionPlan.totalComplexity}")
            
            if (executionPlan.risks.isNotEmpty()) {
                results += "⚠️ RIESGOS IDENTIFICADOS:\n" + executionPlan.risks.joinToString("\n")
            }
        } catch (e: Exception) {
            logExecution(0, "PLANNING_ERROR", e.message ?: "Error en planificación")
        }
        
        // FASE 3: Ejecución principal
        val systemPrompt = buildSystemPrompt(projectMetadata, projectRoot, executionPlan)
        var prompt = "$systemPrompt\n\nSOLICITUD DEL USUARIO:\n$userRequest"
        
        for (round in 0 until maxRounds) {
            roundsUsed = round + 1
            
            val response = runCatching {
                AIProviderManager(context).sendMessageWithAttachments(context, prompt, attachments)
            }.getOrElse { "Error del proveedor IA: ${it.message}" }
            answer = response
            
            logExecution(round, "AI_RESPONSE", response.take(200))
            
            // Extraer y ejecutar herramientas
            val calls = device.extract(response)
            val safeCalls = calls.filterNot { device.requiresApproval(it) }
            val dangerousCalls = calls.filter { device.requiresApproval(it) }
            
            if (dangerousCalls.isNotEmpty()) {
                pending += dangerousCalls.distinctBy { it.args.toString() }
                logExecution(round, "DANGEROUS_CALLS", "${dangerousCalls.size} acciones requieren aprobación")
            }
            
            val roundResults = mutableListOf<String>()
            for (call in safeCalls) {
                val r = executeTool(call, projectId)
                roundResults += "${call.action}: ${r.take(500)}"
                toolCallsExecuted++
                logExecution(round, "TOOL_EXECUTION", "${call.action} ejecutada")
            }
            results += roundResults
            
            // FASE 4: Validación de cambios
            val root = projectId?.let { ProjectService.root(context, it) }
            if (root != null) {
                val files = ProjectService.parseAiFiles(response)
                if (files.isNotEmpty()) {
                    plan = AgentPlanner.fromAiResponse(root, files)
                    
                    // Validar cada cambio propuesto
                    plan.changes.forEach { change ->
                        val validation = validator.validateProposedChange(
                            change,
                            projectMetadata,
                            root
                        )
                        validations.add(validation)
                        
                        if (validation.canApply) {
                            fileChanges++
                            logExecution(round, "CHANGE_VALIDATED", "${change.path} listo para aplicar")
                        } else {
                            logExecution(
                                round, "VALIDATION_FAILED",
                                "${change.path}: ${validation.validation.errors.firstOrNull()?.message}"
                            )
                        }
                    }
                    
                    roundResults += "FILE_PLAN: ${plan.summary}"
                    
                    // Calcular score de validación
                    if (validations.isNotEmpty()) {
                        validationScore = validations.map { it.validation.score }.average().toFloat()
                    }
                }
            }
            
            // Condiciones de salida
            if (calls.isEmpty()) {
                logExecution(round, "NO_MORE_CALLS", "Agente completó el trabajo")
                success = true
                break
            }
            if (roundResults.isEmpty() && dangerousCalls.isEmpty()) {
                logExecution(round, "NO_RESULTS", "Sin resultados significativos")
                success = true
                break
            }
            if (dangerousCalls.isNotEmpty()) {
                logExecution(round, "AWAITING_APPROVAL", "Esperando aprobación del usuario")
                break
            }
            
            // Actualizar contexto para siguiente ronda
            prompt = buildFollowUpPrompt(
                userRequest,
                response,
                roundResults,
                executionPlan?.tasks?.size ?: 0,
                systemPrompt
            )
        }
        
        val executionTime = System.currentTimeMillis() - startTime
        
        val result = EnhancedResult(
            answer = answer,
            toolResults = results,
            pendingApprovals = pending,
            plan = plan,
            executionPlan = executionPlan,
            validations = validations,
            rounds = roundsUsed,
            executionTime = executionTime,
            success = success,
            metadata = ExecutionMetadata(
                tasksCompleted = tasksCompleted,
                toolCallsExecuted = toolCallsExecuted,
                fileChanges = fileChanges,
                validationScore = validationScore,
                agentVersion = "2.0-Enhanced"
            )
        )
        
        // Log final
        logExecution(roundsUsed, "COMPLETION", 
            "Finalizado en ${executionTime}ms, ${toolCallsExecuted} llamadas ejecutadas")
        
        result
    }
    
    private fun buildSystemPrompt(
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>,
        root: File?,
        executionPlan: AdvancedTaskPlanner.ExecutionPlan?
    ): String {
        val projectInfo = if (metadata.isNotEmpty()) {
            val langStats = metadata.values.groupingBy { it.language }.eachCount()
            val totalLines = metadata.values.sumOf { it.lineCount }
            """
            
            INFORMACIÓN DEL PROYECTO:
            - Archivos: ${metadata.size}
            - Líneas totales: $totalLines
            - Lenguajes: ${langStats.entries.joinToString(", ") { "${it.key}(${it.value})" }}
            """.trimIndent()
        } else {
            ""
        }
        
        val taskInfo = if (executionPlan != null) {
            """
            
            PLAN DE EJECUCIÓN:
            - Tareas identificadas: ${executionPlan.tasks.size}
            - Complejidad total: ${executionPlan.totalComplexity}
            - Rondas estimadas: ${executionPlan.estimatedRounds}
            - Orden de ejecución: ${executionPlan.executionOrder.take(5).joinToString(" -> ")}
            """.trimIndent()
        } else {
            ""
        }
        
        return """
Eres TOols AGENT MEJORADO v2.0, un agente ultra-competente y sofisticado.
Capacidades avanzadas:
- Análisis profundo de código y estructura del proyecto
- Descomposición automática de tareas complejas
- Validación exhaustiva de cambios antes de aplicar
- Manejo robusto de errores y recuperación
- Razonamiento de múltiples pasos con memoria de contexto
- Detección de riesgos y sugerencias de mejora

Trabaja con este ciclo mejorado:
1. ENTENDER: Analizar objetivo en profundidad
2. PLANIFICAR: Descomponer en subtareas manejables
3. INVESTIGAR: Recopilar evidencia relevante
4. DISEÑAR: Crear solución considerando dependencias
5. VALIDAR: Verificar cambios antes de aplicar
6. EJECUTAR: Aplicar cambios con herramientas seguras
7. VERIFICAR: Confirmar que el objetivo se cumple
8. ITERAR: Ajustar si es necesario

PRINCIPIOS CRÍTICOS:
- NO inventes datos, rutas, comandos ni éxitos
- SI hay ambigüedad, SIEMPRE inspecciona primero
- Usa herramientas de forma inteligente y eficiente
- Valida SIEMPRE antes de cambios destructivos
- Mantén transparencia sobre lo que haces
- Reporta problemas claramente

HERRAMIENTAS DISPONIBLES:
- Proyecto: project_list, project_read, project_search
- Dispositivo: list, search, read, mkdir, write, rename, copy, move, delete
- Terminal: run_command
- Cambios: Proponer con === FILE: ruta === y bloques ```

FORMATO DE HERRAMIENTAS:
TOOL_CALL {"action":"...","path":"...", ...}

$projectInfo$taskInfo
""".trimIndent()
    }
    
    private fun buildFollowUpPrompt(
        original: String,
        previousResponse: String,
        toolResults: List<String>,
        taskCount: Int,
        systemPrompt: String
    ): String {
        return """
$systemPrompt

CONTEXTO ORIGINAL:
$original

RESPUESTA ANTERIOR:
${previousResponse.take(1000)}

RESULTADOS REALES DE HERRAMIENTAS:
${toolResults.joinToString("\n").take(2000)}

CONTINÚA DESDE AQUÍ:
- Analiza los resultados reales
- Si el objetivo se cumple, responde con resumen preciso
- Si hay errores, adapta tu estrategia
- Si necesitas más información, solicítala
- No repitas las mismas acciones
- Sé eficiente - máximo 2 acciones por ronda
""".trimIndent()
    }
    
    private fun executeTool(call: DeviceAgent.ToolCall, projectId: Int?): String {
        val a = call.args
        return try {
            when (call.action.lowercase()) {
                "list", "search", "read", "mkdir", "write", "rename", "copy", "move", "delete" ->
                    device.execute(call).result.message
                "project_list" -> projectId?.let { 
                    ProjectService.listFiles(ProjectService.root(context, it))
                        .take(500)
                        .joinToString("\n") { f -> ProjectService.safeRelative(ProjectService.root(context, it), f) } 
                } ?: "No hay proyecto abierto."
                "project_read" -> {
                    val id = projectId ?: return "No hay proyecto abierto."
                    val path = a.get("path")?.asString ?: return "Falta path."
                    val root = ProjectService.root(context, id)
                    val f = PathGuard.resolve(root, path)
                    require(f.isFile) { "Archivo no encontrado: $path" }
                    require(f.length() <= 2_000_000) { "Archivo demasiado grande." }
                    f.readText().take(120_000)
                }
                "project_search" -> {
                    val id = projectId ?: return "No hay proyecto abierto."
                    val q = a.get("query")?.asString.orEmpty()
                    require(q.isNotBlank()) { "Consulta vacía." }
                    val root = ProjectService.root(context, id)
                    ProjectService.listFiles(root)
                        .filter { it.length() <= 500_000 && runCatching { it.readText().contains(q, true) }.getOrDefault(false) }
                        .take(500)
                        .joinToString("\n") { ProjectService.safeRelative(root, it) }
                        .ifBlank { "Sin coincidencias." }
                }
                "run_command" -> {
                    val id = projectId ?: return "No hay proyecto abierto para ejecutar comandos."
                    val command = a.get("command")?.asString.orEmpty()
                    require(command.isNotBlank()) { "Comando vacío." }
                    shell.execute(id, command, approved = false).output
                }
                else -> "Acción no reconocida: ${call.action}"
            }
        } catch (e: Exception) {
            "Error en ${call.action}: ${e.message}"
        }
    }
    
    private fun logExecution(round: Int, action: String, result: String) {
        executionLog.add(ExecutionLogEntry(round, action, result))
    }
    
    fun getExecutionLog(): List<ExecutionLogEntry> = executionLog.toList()
}
