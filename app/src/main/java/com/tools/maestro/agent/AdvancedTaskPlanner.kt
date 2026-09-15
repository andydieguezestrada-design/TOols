package com.tools.maestro.agent

import java.io.File

/**
 * Planificador avanzado que descompone tareas complejas en subtareas manejables
 * y ordena su ejecución considerando dependencias.
 */
class AdvancedTaskPlanner(private val analyzer: EnhancedCodeAnalyzer = EnhancedCodeAnalyzer()) {
    
    data class Task(
        val id: String,
        val title: String,
        val description: String,
        val priority: Int = 0,
        val dependencies: List<String> = emptyList(),
        val estimatedComplexity: Complexity = Complexity.MEDIUM,
        val requiredTools: List<String> = emptyList(),
        val acceptanceCriteria: List<String> = emptyList(),
        val status: TaskStatus = TaskStatus.PENDING
    )
    
    data class ExecutionPlan(
        val originalRequest: String,
        val tasks: List<Task>,
        val totalComplexity: Complexity,
        val executionOrder: List<String>,
        val risks: List<String> = emptyList(),
        val estimatedRounds: Int = 0
    )
    
    enum class Complexity {
        TRIVIAL, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    enum class TaskStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, BLOCKED
    }
    
    data class PlanContext(
        val projectRoot: File? = null,
        val projectMetadata: Map<String, EnhancedCodeAnalyzer.FileMetadata> = emptyMap(),
        val availableTools: List<String> = emptyList(),
        val constraints: List<String> = emptyList()
    )
    
    fun planUserRequest(
        userRequest: String,
        context: PlanContext
    ): ExecutionPlan {
        // Analizar el proyecto si se proporciona
        val metadata = if (context.projectRoot != null) {
            analyzer.analyzeProject(context.projectRoot)
        } else {
            context.projectMetadata
        }
        
        // Descomponer la solicitud en tareas
        val tasks = decomposeRequest(userRequest, metadata, context)
        
        // Ordenar tareas según dependencias
        val executionOrder = topologicalSort(tasks)
        
        // Calcular complejidad total
        val totalComplexity = calculateTotalComplexity(tasks)
        
        // Identificar riesgos
        val risks = identifyRisks(tasks, metadata)
        
        // Estimar rondas necesarias
        val estimatedRounds = estimateRounds(tasks)
        
        return ExecutionPlan(
            originalRequest = userRequest,
            tasks = tasks,
            totalComplexity = totalComplexity,
            executionOrder = executionOrder,
            risks = risks,
            estimatedRounds = estimatedRounds
        )
    }
    
    private fun decomposeRequest(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>,
        context: PlanContext
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        val lowerRequest = request.lowercase()
        
        // Detectar tipo de tarea y crear subtareas
        when {
            lowerRequest.contains("refactor") -> {
                tasks += createRefactoringTasks(request, metadata)
            }
            lowerRequest.contains("test") || lowerRequest.contains("testing") -> {
                tasks += createTestingTasks(request, metadata)
            }
            lowerRequest.contains("fix") || lowerRequest.contains("bug") -> {
                tasks += createBugFixTasks(request, metadata)
            }
            lowerRequest.contains("feature") || lowerRequest.contains("agregar") -> {
                tasks += createFeatureTasks(request, metadata)
            }
            lowerRequest.contains("documen") -> {
                tasks += createDocumentationTasks(request, metadata)
            }
            lowerRequest.contains("analyze") || lowerRequest.contains("analizar") -> {
                tasks += createAnalysisTasks(request, metadata)
            }
            else -> {
                tasks += createGenericTasks(request, metadata)
            }
        }
        
        return tasks
    }
    
    private fun createRefactoringTasks(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        
        tasks.add(Task(
            id = "analyze-code",
            title = "Analizar código para identificar oportunidades de refactoring",
            description = "Revisar el código actual e identificar áreas de mejora",
            priority = 1,
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("project_read", "project_search"),
            acceptanceCriteria = listOf("Identificar al menos 3 áreas de mejora", "Documentar hallazgos")
        ))
        
        tasks.add(Task(
            id = "plan-refactoring",
            title = "Crear plan de refactoring",
            description = "Diseñar estrategia de refactoring manteniendo compatibilidad",
            priority = 2,
            dependencies = listOf("analyze-code"),
            estimatedComplexity = Complexity.HIGH,
            acceptanceCriteria = listOf("Plan detallado", "Identificar cambios necesarios")
        ))
        
        tasks.add(Task(
            id = "implement-refactoring",
            title = "Implementar cambios de refactoring",
            description = "Aplicar los cambios según el plan",
            priority = 3,
            dependencies = listOf("plan-refactoring"),
            estimatedComplexity = Complexity.HIGH,
            requiredTools = listOf("write"),
            acceptanceCriteria = listOf("Cambios aplicados", "Código mejorado")
        ))
        
        tasks.add(Task(
            id = "verify-refactoring",
            title = "Verificar integridad después de refactoring",
            description = "Ejecutar pruebas y validar que todo funciona",
            priority = 4,
            dependencies = listOf("implement-refactoring"),
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("run_command"),
            acceptanceCriteria = listOf("Pruebas pasadas", "Funcionamiento verificado")
        ))
        
        return tasks
    }
    
    private fun createTestingTasks(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        
        tasks.add(Task(
            id = "identify-untested",
            title = "Identificar código sin pruebas",
            description = "Analizar cobertura y encontrar funciones sin tests",
            priority = 1,
            estimatedComplexity = Complexity.LOW,
            requiredTools = listOf("project_search", "project_read")
        ))
        
        tasks.add(Task(
            id = "write-tests",
            title = "Escribir pruebas unitarias",
            description = "Crear pruebas para funciones identificadas",
            priority = 2,
            dependencies = listOf("identify-untested"),
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("write")
        ))
        
        tasks.add(Task(
            id = "run-tests",
            title = "Ejecutar suite de pruebas",
            description = "Ejecutar todas las pruebas y verificar que pasan",
            priority = 3,
            dependencies = listOf("write-tests"),
            estimatedComplexity = Complexity.LOW,
            requiredTools = listOf("run_command")
        ))
        
        return tasks
    }
    
    private fun createBugFixTasks(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        
        tasks.add(Task(
            id = "reproduce-bug",
            title = "Reproducir el bug",
            description = "Entender y reproducir el comportamiento defectuoso",
            priority = 1,
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("project_read", "run_command")
        ))
        
        tasks.add(Task(
            id = "root-cause-analysis",
            title = "Análisis de causa raíz",
            description = "Identificar la causa del bug",
            priority = 2,
            dependencies = listOf("reproduce-bug"),
            estimatedComplexity = Complexity.HIGH,
            requiredTools = listOf("project_search")
        ))
        
        tasks.add(Task(
            id = "implement-fix",
            title = "Implementar corrección",
            description = "Escribir el código para corregir el bug",
            priority = 3,
            dependencies = listOf("root-cause-analysis"),
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("write")
        ))
        
        tasks.add(Task(
            id = "test-fix",
            title = "Verificar que el bug está solucionado",
            description = "Confirmar que el fix funciona y no introduce nuevos bugs",
            priority = 4,
            dependencies = listOf("implement-fix"),
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("run_command")
        ))
        
        return tasks
    }
    
    private fun createFeatureTasks(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        
        tasks.add(Task(
            id = "design-feature",
            title = "Diseñar la característica",
            description = "Planificar implementación y API",
            priority = 1,
            estimatedComplexity = Complexity.HIGH,
            acceptanceCriteria = listOf("Diseño documentado", "Interfaz clara")
        ))
        
        tasks.add(Task(
            id = "implement-feature",
            title = "Implementar la característica",
            description = "Escribir el código de la característica",
            priority = 2,
            dependencies = listOf("design-feature"),
            estimatedComplexity = Complexity.HIGH,
            requiredTools = listOf("write", "mkdir")
        ))
        
        tasks.add(Task(
            id = "test-feature",
            title = "Crear pruebas para la característica",
            description = "Escribir tests unitarios e integración",
            priority = 3,
            dependencies = listOf("implement-feature"),
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("write")
        ))
        
        tasks.add(Task(
            id = "verify-feature",
            title = "Verificar características",
            description = "Ejecutar tests y validar funcionamiento",
            priority = 4,
            dependencies = listOf("test-feature"),
            estimatedComplexity = Complexity.LOW,
            requiredTools = listOf("run_command")
        ))
        
        return tasks
    }
    
    private fun createDocumentationTasks(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        
        tasks.add(Task(
            id = "audit-docs",
            title = "Auditar documentación existente",
            description = "Revisar qué está documentado y qué falta",
            priority = 1,
            estimatedComplexity = Complexity.LOW,
            requiredTools = listOf("project_search")
        ))
        
        tasks.add(Task(
            id = "write-docs",
            title = "Escribir/actualizar documentación",
            description = "Crear o mejorar documentación",
            priority = 2,
            dependencies = listOf("audit-docs"),
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("write")
        ))
        
        return tasks
    }
    
    private fun createAnalysisTasks(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<Task> {
        val tasks = mutableListOf<Task>()
        
        tasks.add(Task(
            id = "initial-analysis",
            title = "Análisis inicial del código",
            description = "Examinar estructura y patrones",
            priority = 1,
            estimatedComplexity = Complexity.MEDIUM,
            requiredTools = listOf("project_read", "project_search")
        ))
        
        tasks.add(Task(
            id = "detailed-report",
            title = "Generar reporte detallado",
            description = "Crear análisis comprehensivo con hallazgos",
            priority = 2,
            dependencies = listOf("initial-analysis"),
            estimatedComplexity = Complexity.MEDIUM
        ))
        
        return tasks
    }
    
    private fun createGenericTasks(
        request: String,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<Task> {
        return listOf(
            Task(
                id = "understand-request",
                title = "Entender solicitud",
                description = request,
                priority = 1,
                estimatedComplexity = Complexity.MEDIUM
            ),
            Task(
                id = "analyze-impact",
                title = "Analizar impacto",
                description = "Entender qué archivos y sistemas se ven afectados",
                priority = 2,
                dependencies = listOf("understand-request"),
                estimatedComplexity = Complexity.MEDIUM
            ),
            Task(
                id = "execute-changes",
                title = "Ejecutar cambios",
                description = "Implementar los cambios necesarios",
                priority = 3,
                dependencies = listOf("analyze-impact"),
                estimatedComplexity = Complexity.HIGH
            ),
            Task(
                id = "verify-completion",
                title = "Verificar completitud",
                description = "Confirmar que la solicitud se completó correctamente",
                priority = 4,
                dependencies = listOf("execute-changes"),
                estimatedComplexity = Complexity.MEDIUM
            )
        )
    }
    
    private fun topologicalSort(tasks: List<Task>): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        
        fun visit(taskId: String) {
            if (taskId in visited) return
            if (taskId in visiting) return // Ciclo detectado
            
            visiting.add(taskId)
            
            val task = tasks.find { it.id == taskId }
            task?.dependencies?.forEach { visit(it) }
            
            visiting.remove(taskId)
            visited.add(taskId)
            result.add(taskId)
        }
        
        tasks.forEach { visit(it.id) }
        return result
    }
    
    private fun calculateTotalComplexity(tasks: List<Task>): Complexity {
        val max = tasks.maxOfOrNull { it.estimatedComplexity.ordinal } ?: 0
        return Complexity.values()[max.coerceIn(0, Complexity.values().size - 1)]
    }
    
    private fun identifyRisks(
        tasks: List<Task>,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>
    ): List<String> {
        val risks = mutableListOf<String>()
        
        if (tasks.any { it.estimatedComplexity == Complexity.CRITICAL }) {
            risks.add("Incluye tareas de complejidad crítica - requiere validación exhaustiva")
        }
        
        val fileCount = metadata.size
        if (fileCount > 100) {
            risks.add("Proyecto grande (${fileCount} archivos) - más tiempo para análisis")
        }
        
        val depGraph = tasks.groupBy { it.id }.mapValues { (_, v) -> v.firstOrNull()?.dependencies ?: emptyList() }
        if (hasCircularDependencies(depGraph)) {
            risks.add("Posibles dependencias circulares en tareas")
        }
        
        if (tasks.any { it.requiredTools.contains("run_command") } && tasks.size > 5) {
            risks.add("Múltiples ejecuciones de comandos - puede ser lento")
        }
        
        return risks
    }
    
    private fun hasCircularDependencies(graph: Map<String, List<String>>): Boolean {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        
        fun hasCycle(node: String): Boolean {
            if (node in visited) return false
            if (node in visiting) return true
            
            visiting.add(node)
            graph[node]?.forEach { if (hasCycle(it)) return true }
            visiting.remove(node)
            visited.add(node)
            
            return false
        }
        
        return graph.keys.any { hasCycle(it) }
    }
    
    private fun estimateRounds(tasks: List<Task>): Int {
        var rounds = 0
        val visited = mutableSetOf<String>()
        
        fun estimateDepth(taskId: String): Int {
            if (taskId in visited) return 0
            visited.add(taskId)
            
            val task = tasks.find { it.id == taskId } ?: return 0
            if (task.dependencies.isEmpty()) return 1
            
            return 1 + (task.dependencies.maxOfOrNull { estimateDepth(it) } ?: 1)
        }
        
        rounds = tasks.maxOfOrNull { estimateDepth(it.id) } ?: 1
        return (rounds * 1.5).toInt().coerceAtLeast(1).coerceAtMost(10)
    }
    
}
