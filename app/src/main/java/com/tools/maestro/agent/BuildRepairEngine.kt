package com.tools.maestro.agent

/** Builds a bounded repair prompt from real CI output. It never writes files by itself. */
object BuildRepairEngine {
    fun prompt(projectContext: String, buildLog: String, attempt: Int): String = """
        Eres el agente de reparación de TOols.
        Esta es la tentativa $attempt de un máximo de 3.
        Analiza SOLO el error real del build y el contexto suministrado.
        No inventes dependencias ni archivos innecesarios.
        Devuelve primero un diagnóstico corto y después, si hace falta, archivos completos usando:
        === FILE: ruta/archivo ===
        ```text
        contenido completo
        ```
        No ejecutes comandos y no afirmes que una compilación pasó si no aparece en el log.

        ===== CONTEXTO DEL PROYECTO =====
        $projectContext

        ===== LOG REAL DE CI =====
        $buildLog
    """.trimIndent()
}
