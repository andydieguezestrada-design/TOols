package com.tools.maestro.agent

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

/** Local model controller: health, model discovery and capability hints for Ollama/llama.cpp-compatible servers. */
class LocalModelController(private val context: Context) {
    data class LocalProfile(val id: String, val name: String, val endpoint: String, val model: String, val contextTokens: Int, val codeFocused: Boolean, val embeddings: Boolean, val embeddingModel: String)
    data class Health(val online: Boolean, val endpoint: String, val models: List<String> = emptyList(), val message: String = "")

    private val prefs = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    fun profile(): LocalProfile = LocalProfile(
        id = prefs.getString("id", "qwen3-coder") ?: "qwen3-coder",
        name = prefs.getString("name", "Qwen Coder local") ?: "Qwen Coder local",
        endpoint = prefs.getString("endpoint", "http://127.0.0.1:11434/v1/chat/completions") ?: "http://127.0.0.1:11434/v1/chat/completions",
        model = prefs.getString("model", "qwen3:4b") ?: "qwen3:4b",
        contextTokens = prefs.getInt("context", 32768),
        codeFocused = prefs.getBoolean("code", true),
        embeddings = prefs.getBoolean("embeddings", true),
        embeddingModel = prefs.getString("embeddingModel", "embeddinggemma") ?: "embeddinggemma"
    )

    fun save(profile: LocalProfile) = prefs.edit()
        .putString("id", profile.id).putString("name", profile.name).putString("endpoint", profile.endpoint)
        .putString("model", profile.model).putInt("context", profile.contextTokens)
        .putBoolean("code", profile.codeFocused).putBoolean("embeddings", profile.embeddings).putString("embeddingModel", profile.embeddingModel).apply()

    fun embed(texts: List<String>): List<List<Float>> {
        if (texts.isEmpty()) return emptyList()
        val p = profile()
        if (!p.embeddings) return emptyList()
        val base = p.endpoint.substringBefore("/v1/").trimEnd('/')
        val (url, body) = if (base.contains(":11434")) {
            "$base/api/embed" to gson.toJson(mapOf("model" to p.embeddingModel, "input" to texts.take(32)))
        } else {
            "$base/v1/embeddings" to gson.toJson(mapOf("model" to p.embeddingModel, "input" to texts.take(32), "encoding_format" to "float"))
        }
        return runCatching {
            val req = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                val root = gson.fromJson(r.body?.string().orEmpty(), JsonObject::class.java)
                root.getAsJsonArray("embeddings")?.let { arr ->
                    return@use arr.map { element -> element.asJsonArray.map { it.asFloat } }
                }
                val arr = root.getAsJsonArray("data") ?: return emptyList()
                arr.mapNotNull { element ->
                    val values = element.asJsonObject.getAsJsonArray("embedding")
                    values?.map { it.asFloat }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun health(): Health {
        val p = profile()
        val base = p.endpoint.substringBefore("/v1/").trimEnd('/')
        val url = "$base/v1/models"
        return runCatching {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) return Health(false, p.endpoint, message = "HTTP ${r.code}: ${raw.take(300)}")
                val root = gson.fromJson(raw, JsonObject::class.java)
                val models = root.getAsJsonArray("data")?.mapNotNull { it.asJsonObject?.get("id")?.asString }.orEmpty()
                Health(true, p.endpoint, models, "Motor local disponible")
            }
        }.getOrElse { Health(false, p.endpoint, message = it.message ?: "Motor local no disponible") }
    }
}
