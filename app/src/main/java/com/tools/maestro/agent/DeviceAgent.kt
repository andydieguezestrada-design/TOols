package com.tools.maestro.agent

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tools.maestro.core.fs.DeviceFileManager

/** Executes structured AI tool calls against shared Android storage. Safe actions run automatically; destructive ones require approval. */
class DeviceAgent(private val files: DeviceFileManager = DeviceFileManager()) {
    data class ToolCall(val action: String, val args: JsonObject)
    data class Execution(val call: ToolCall, val result: DeviceFileManager.Result, val requiresApproval: Boolean)

    private val gson = Gson()

    fun extract(text: String): List<ToolCall> {
        val out = mutableListOf<ToolCall>()
        var cursor = 0
        while (true) {
            val marker = text.indexOf("TOOL_CALL", cursor, ignoreCase = true)
            if (marker < 0) break
            var i = marker + "TOOL_CALL".length
            while (i < text.length && text[i].isWhitespace()) i++
            if (i >= text.length || text[i] != '{') { cursor = i; continue }
            var depth = 0
            var inString = false
            var escaped = false
            var end = -1
            for (j in i until text.length) {
                val ch = text[j]
                if (inString) {
                    if (escaped) escaped = false
                    else if (ch == '\\') escaped = true
                    else if (ch == '"') inString = false
                } else when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { end = j; break } }
                }
            }
            if (end > i) {
                runCatching {
                    val o = JsonParser.parseString(text.substring(i, end + 1)).asJsonObject
                    val action = o.get("action")?.asString ?: return@runCatching
                    out += ToolCall(action, o)
                }
                cursor = end + 1
            } else break
        }
        return out.distinctBy { it.action.lowercase() + "|" + it.args.toString() }
    }

    fun requiresApproval(call: ToolCall): Boolean = when (call.action.lowercase()) {
        "delete", "move", "copy", "rename" -> true
        "write" -> true
        "mkdir" -> false
        else -> false
    }

    fun execute(call: ToolCall, approved: Boolean = false): Execution {
        if (!files.hasFullAccess()) return Execution(call, DeviceFileManager.Result(false, "TOols no tiene acceso a los archivos del dispositivo. Activa 'Permitir administrar todos los archivos' en Ajustes."), false)
        val needs = requiresApproval(call)
        if (needs && !approved) return Execution(call, DeviceFileManager.Result(false, "ACCIÓN BLOQUEADA: requiere aprobación del usuario."), true)
        val a = call.args
        val r = when (call.action.lowercase()) {
            "list" -> files.list(a.string("path", ""))
            "tree" -> files.tree(a.string("path", ""), a.get("depth")?.asInt ?: 3)
            "search" -> files.search(a.string("query", ""), a.string("path", ""))
            "search_content" -> files.searchContent(a.string("query", ""), a.string("path", ""))
            "inspect" -> files.inspect(a.string("path", ""))
            "read" -> files.read(a.string("path", ""))
            "mkdir" -> files.mkdir(a.string("path", ""))
            "write" -> files.write(a.string("path", ""), a.string("content", ""))
            "rename" -> files.rename(a.string("path", ""), a.string("newName", ""))
            "copy" -> files.copy(a.string("path", ""), a.string("destination", ""))
            "move" -> files.move(a.string("path", ""), a.string("destination", ""))
            "delete" -> files.delete(a.string("path", ""), a.bool("recursive", false))
            else -> DeviceFileManager.Result(false, "Acción no permitida: ${call.action}")
        }
        return Execution(call, r, needs)
    }

    fun systemPrompt(): String = """
Eres el agente operativo de TOols. Puedes gestionar el almacenamiento compartido del teléfono Android mediante herramientas.
Cuando una tarea requiera archivos, emite llamadas estructuradas en líneas con este formato EXACTO:
TOOL_CALL {\"action\":\"list|search|read|mkdir|write|rename|copy|move|delete\",\"path\":\"ruta/relativa\", ...}
Rutas relativas a /storage/emulated/0. Argumentos: tree=path+depth; search=query+path; search_content=query+path; inspect=path; write=path+content; rename=path+newName; copy/move=path+destination; delete=path+recursive.
Puedes encadenar varias llamadas. No inventes resultados: usa primero list/search/read cuando necesites conocer el dispositivo.
No uses rutas /Android/data o /Android/obb ni intentes saltarte restricciones del sistema.
Las operaciones que puedan destruir, mover, copiar, renombrar o sobrescribir datos serán sometidas a aprobación explícita por TOols antes de ejecutarse.
""".trimIndent()

    private fun JsonObject.string(name: String, default: String) = get(name)?.asString ?: default
    private fun JsonObject.bool(name: String, default: Boolean) = get(name)?.asBoolean ?: default
}
