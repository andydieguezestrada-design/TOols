package com.tools.maestro.ai.provider

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tools.maestro.agent.LocalModelController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Online multi-provider client. Provider order is deterministic and failures automatically fall through. */
class AIProviderManager(private val context: Context) {
    private val prefs = runCatching {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "ai_providers_secure", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse { context.getSharedPreferences("ai_providers", Context.MODE_PRIVATE) }
    private val gson = Gson()
    // Reutiliza conexiones y reduce la latencia de cada ronda del agente.
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    data class Provider(val id: String, val name: String, val key: String, val model: String, val endpoint: String, val enabled: Boolean, val priority: Int, val local: Boolean = false)
    data class Attachment(val uri: Uri, val name: String, val mime: String, val size: Long)
    data class ProviderAttempt(val provider: String, val ok: Boolean, val message: String)

    private fun emit(onProgress: ((String) -> Unit)?, message: String) {
        runCatching { onProgress?.invoke(message) }
    }

    fun saveProvider(id: String, key: String, model: String, endpoint: String, enabled: Boolean, priority: Int) {
        prefs.edit().putString("${id}_key", key.trim()).putString("${id}_model", model.trim()).putString("${id}_endpoint", endpoint.trim()).putBoolean("${id}_enabled", enabled).putInt("${id}_priority", priority).apply()
        if (id == "local") {
            val old = LocalModelController(context).profile()
            LocalModelController(context).save(old.copy(model = model.trim(), endpoint = endpoint.trim()))
        }
        if (!enabled) prefs.edit().remove("${id}_cooldown_until").apply()
    }

    private fun cooldownUntil(id: String): Long = prefs.getLong("${id}_cooldown_until", 0L)
    private fun isAvailable(p: Provider): Boolean = cooldownUntil(p.id) <= System.currentTimeMillis()
    private fun markCooldown(p: Provider, e: Throwable) {
        val m = e.message.orEmpty().lowercase()
        val seconds = when {
            m.contains("http 429") -> 60L * 60L
            m.contains("http 403") -> 15L * 60L
            m.contains("http 401") -> 5L * 60L
            m.contains("timeout") || m.contains("unable to resolve host") || m.contains("unknownhost") -> 60L
            else -> 30L
        }
        prefs.edit().putLong("${p.id}_cooldown_until", System.currentTimeMillis() + seconds * 1000L).apply()
    }

    fun providerStatus(): List<String> = allProviders().map { p ->
        val remaining = (cooldownUntil(p.id) - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L
        if (remaining > 0) "${p.name}: en espera ${remaining}s" else "${p.name}: disponible"
    }

    private fun allProviders(): List<Provider> = listOf(
        provider("gemini", "Gemini", "gemini-3.6-flash", "https://generativelanguage.googleapis.com/v1beta/models", 0),
        provider("groq", "Groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1/chat/completions", 1),
        provider("mistral", "Mistral", "mistral-small-latest", "https://api.mistral.ai/v1/chat/completions", 2),
        localProvider()
    )

    fun providers(): List<Provider> = allProviders()
        .filter { it.enabled && it.endpoint.isNotBlank() && (it.local || it.key.isNotBlank()) }
        .filter { isAvailable(it) }
        .sortedBy { it.priority }

    private fun provider(id: String, name: String, model: String, endpoint: String, priority: Int, local: Boolean = false) =
        Provider(id, name, prefs.getString("${id}_key", "") ?: "", prefs.getString("${id}_model", model) ?: model,
            prefs.getString("${id}_endpoint", endpoint) ?: endpoint, prefs.getBoolean("${id}_enabled", local),
            prefs.getInt("${id}_priority", priority), local)

    private fun localProvider(): Provider {
        val profile = LocalModelController(context).profile()
        return Provider(
            id = "local", name = profile.name, key = "", model = profile.model, endpoint = profile.endpoint,
            enabled = prefs.getBoolean("local_enabled", true), priority = prefs.getInt("local_priority", 99), local = true
        )
    }

    suspend fun testProvider(id: String): Result<String> = withContext(Dispatchers.IO) {
        val p = providers().firstOrNull { it.id == id }
            ?: return@withContext Result.failure(IllegalStateException("Proveedor no configurado o desactivado."))
        runCatching {
            val answer = when (p.id) {
                "gemini" -> callGemini(p, "Responde únicamente: OK")
                else -> callOpenAiCompatibleWithFallback(p, "Responde únicamente: OK")
            }
            if (answer.isBlank()) error("El proveedor respondió vacío.")
            "Conexión correcta con ${p.name} (${p.model})"
        }.recoverCatching { e ->
            throw IllegalStateException(formatProviderError(p, e), e)
        }
    }

    suspend fun sendMessageWithAttachments(
        context: Context,
        message: String,
        attachments: List<Attachment>,
        onProgress: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val list = providers()
        if (list.isEmpty()) return@withContext "No hay proveedor configurado. Abre Proveedores y añade una API key."
        val errors = mutableListOf<String>()
        for (p in list) {
            emit(onProgress, "Conectando con ${p.name} · ${p.model}")
            val started = System.currentTimeMillis()
            try {
                val result = when {
                    p.id == "gemini" -> callGeminiWithAttachments(context, p, message, attachments)
                    attachments.all { isTextLike(it.mime, it.name) } -> {
                        val extracted = attachments.joinToString("\n\n") { a ->
                            val text = context.contentResolver.openInputStream(a.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                            "=== ${a.name} ===\n${text.take(50000)}"
                        }
                        callOpenAiCompatibleWithFallback(p, "$message\n\nARCHIVOS ADJUNTOS:\n$extracted")
                    }
                    else -> error("${p.name} no admite este tipo de archivo mediante su API actual; se intentará el siguiente proveedor.")
                }
                emit(onProgress, "${p.name} respondió en ${System.currentTimeMillis() - started} ms")
                return@withContext result
            } catch (e: Exception) {
                markCooldown(p, e)
                val detail = formatProviderError(p, e)
                errors += "${p.name}: $detail"
                emit(onProgress, "${p.name} falló · ${detail.take(180)} · cambiando automáticamente")
            }
        }
        "Todos los proveedores disponibles fallaron:\n${errors.joinToString("\n")}"
    }

    private fun isTextLike(mime: String, name: String): Boolean {
        val n = name.lowercase()
        return mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("csv") ||
            n.endsWith(".md") || n.endsWith(".kt") || n.endsWith(".java") || n.endsWith(".py") || n.endsWith(".txt") || n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".csv")
    }

    private fun callGeminiWithAttachments(context: Context, p: Provider, message: String, attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return callGemini(p, message)
        val fileUris = attachments.map { uploadGeminiFile(context, p.key, it) }
        val parts = mutableListOf<Map<String, Any>>(mapOf("text" to message))
        attachments.zip(fileUris).forEach { (a, uri) -> parts += mapOf("file_data" to mapOf("mime_type" to a.mime, "file_uri" to uri)) }
        val body = gson.toJson(mapOf("contents" to listOf(mapOf("parts" to parts))))
        val url = "${p.endpoint.trimEnd('/')}/${p.model}:generateContent"
        return request(url, mapOf("x-goog-api-key" to p.key), body) { root ->
            root.getAsJsonArray("candidates")?.get(0)?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")?.let { arr ->
                (0 until arr.size()).joinToString("\n") { i -> arr.get(i).asJsonObject.get("text")?.asString.orEmpty() }.trim()
            }?.takeIf { it.isNotBlank() } ?: error("Gemini no devolvió texto.")
        }
    }

    private fun uploadGeminiFile(context: Context, apiKey: String, attachment: Attachment): String {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(attachment.uri) ?: error("No se pudo leer ${attachment.name}")
        val size = attachment.size.takeIf { it >= 0 } ?: resolver.openAssetFileDescriptor(attachment.uri, "r")?.use { it.length } ?: -1L
        if (size > 50L * 1024L * 1024L && attachment.mime == "application/pdf") error("PDF demasiado grande para el chatbot: máximo 50 MB.")
        val startBody = gson.toJson(mapOf("file" to mapOf("display_name" to attachment.name.take(120))))
        val start = Request.Builder().url("https://generativelanguage.googleapis.com/upload/v1beta/files")
            .post(startBody.toRequestBody("application/json".toMediaType()))
            .header("x-goog-api-key", apiKey)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Length", size.toString())
            .header("X-Goog-Upload-Header-Content-Type", attachment.mime)
            .build()
        val uploadUrl = client.newCall(start).execute().use { r ->
            if (!r.isSuccessful) error("Gemini upload start HTTP ${r.code}: ${r.body?.string()?.take(500)}")
            r.header("X-Goog-Upload-URL") ?: r.header("x-goog-upload-url") ?: error("Gemini no devolvió X-Goog-Upload-URL")
        }
        val bytes = input.use { it.readBytes() }
        val upload = Request.Builder().url(uploadUrl)
            .post(bytes.toRequestBody(attachment.mime.toMediaType()))
            .header("Content-Length", bytes.size.toString())
            .header("X-Goog-Upload-Offset", "0")
            .header("X-Goog-Upload-Command", "upload, finalize")
            .build()
        return client.newCall(upload).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("Gemini upload HTTP ${r.code}: ${raw.take(500)}")
            gson.fromJson(raw, JsonObject::class.java)?.getAsJsonObject("file")?.get("uri")?.asString
                ?: error("Gemini no devolvió la URI del archivo.")
        }
    }

    suspend fun sendMessage(message: String, onProgress: ((String) -> Unit)? = null): String = withContext(Dispatchers.IO) {
        val list = providers(); if (list.isEmpty()) return@withContext "No hay proveedor configurado. Abre Proveedores y añade una API key."
        val errors = mutableListOf<String>()
        for (p in list) {
            emit(onProgress, "Conectando con ${p.name} · ${p.model}")
            try {
                val result = when (p.id) { "gemini" -> callGemini(p, message); else -> callOpenAiCompatibleWithFallback(p, message) }
                emit(onProgress, "Respuesta recibida de ${p.name}")
                return@withContext result
            } catch (e: Exception) {
                markCooldown(p, e)
                val detail = formatProviderError(p, e)
                errors += "${p.name}: $detail"
                emit(onProgress, "${p.name} falló · ${detail.take(180)} · cambiando automáticamente")
            }
        }
        "Todos los proveedores fallaron:\n${errors.joinToString("\n")}"
    }
    private fun callGemini(p: Provider, message: String): String {
        val body = gson.toJson(mapOf(
            "contents" to listOf(mapOf("parts" to listOf(mapOf("text" to message)))),
            "generationConfig" to mapOf("maxOutputTokens" to 4096)
        ))
        val url = "${p.endpoint.trimEnd('/')}/${p.model}:generateContent"
        return request(url, mapOf("x-goog-api-key" to p.key), body) { root ->
            root.getAsJsonArray("candidates")?.get(0)?.asJsonObject
                ?.getAsJsonObject("content")?.getAsJsonArray("parts")?.get(0)?.asJsonObject
                ?.get("text")?.asString ?: error("Gemini no devolvió texto.")
        }
    }
    private fun callOpenAiCompatibleWithFallback(p: Provider, message: String): String {
        return try {
            callOpenAiCompatible(p, message)
        } catch (e: Exception) {
            val isGroqRestrictedModel = p.id == "groq" && p.model == "openai/gpt-oss-120b" &&
                e.message.orEmpty().contains("HTTP 403")
            if (!isGroqRestrictedModel) throw e
            // Groq puede bloquear un modelo por permisos de proyecto/organización.
            // No cambiamos la configuración guardada: hacemos un segundo intento temporal.
            callOpenAiCompatible(p.copy(model = "openai/gpt-oss-20b"), message)
        }
    }

    private fun callOpenAiCompatible(p: Provider, message: String): String {
        val body = gson.toJson(mapOf(
            "model" to p.model,
            "messages" to listOf(mapOf("role" to "user", "content" to message)),
            "stream" to false,
            "max_tokens" to 4096
        ))
        return request(p.endpoint.trimEnd('/'), mapOf("Authorization" to "Bearer ${p.key}"), body) { root ->
            root.getAsJsonArray("choices")?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")?.get("content")?.asString
                ?: error("${p.name} no devolvió contenido compatible con OpenAI.")
        }
    }
    private fun callAnthropic(p: Provider, message: String): String {
        val body = gson.toJson(mapOf("model" to p.model, "max_tokens" to 4096, "messages" to listOf(mapOf("role" to "user", "content" to message))))
        return request(p.endpoint.trimEnd('/'), mapOf("x-api-key" to p.key, "anthropic-version" to "2023-06-01"), body) { root ->
            root.getAsJsonArray("content")?.get(0)?.asJsonObject?.get("text")?.asString
                ?: error("Anthropic no devolvió texto.")
        }
    }
    private fun formatProviderError(p: Provider, e: Throwable): String {
        val raw = e.message.orEmpty().replace('\n', ' ').trim()
        val lower = raw.lowercase()
        return when {
            lower.contains("http 403") && p.id == "groq" ->
                "HTTP 403: Groq rechazó el acceso. Puede ser una restricción del modelo/proyecto; revisa permisos del modelo y la API key. No es un fallo de la UI."
            lower.contains("http 401") -> "HTTP 401: API key inválida, expirada o no autorizada."
            lower.contains("http 429") -> "HTTP 429: límite/cuota alcanzado; se intentará otro proveedor."
            lower.contains("unable to resolve host") || lower.contains("unknownhost") -> "Sin resolución DNS/conectividad hacia el proveedor."
            lower.contains("timeout") -> "Tiempo de espera agotado; se intentará otro proveedor."
            else -> raw.ifBlank { "Error de conexión desconocido." }
        }
    }

    private fun request(url: String, headers: Map<String,String>, body: String, parse: (JsonObject) -> String): String {
        val b = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType()))
        b.header("Accept", "application/json")
        headers.forEach { b.header(it.key, it.value) }
        client.newCall(b.build()).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val detail = runCatching {
                    gson.fromJson(raw, JsonObject::class.java)?.let { root ->
                        root.get("error")?.asJsonObject?.get("message")?.asString
                            ?: root.get("message")?.asString
                            ?: root.get("error")?.asString
                    }
                }.getOrNull()
                val suffix = detail?.takeIf { it.isNotBlank() } ?: raw.take(500)
                error("HTTP ${r.code}: $suffix")
            }
            if (raw.isBlank()) error("Respuesta vacía del proveedor.")
            return parse(gson.fromJson(raw, JsonObject::class.java))
        }
    }
}
