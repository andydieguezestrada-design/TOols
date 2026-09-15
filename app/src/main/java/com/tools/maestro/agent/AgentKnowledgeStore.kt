package com.tools.maestro.agent

import android.content.Context
import com.google.gson.GsonBuilder
import java.io.File
import java.security.MessageDigest

/**
 * Persistent local RAG index for projects. It is intentionally provider-independent:
 * lexical retrieval always works, while optional local embeddings can be layered later.
 * The index stores only extracted text/metadata, never API keys.
 */
class AgentKnowledgeStore(private val context: Context) {
    data class Chunk(
        val id: String,
        val path: String,
        val language: String,
        val startLine: Int,
        val endLine: Int,
        val text: String,
        val hash: String,
        val symbols: List<String> = emptyList(),
        val embedding: List<Float> = emptyList()
    )

    data class Index(
        val version: Int = 2,
        val projectId: Int? = null,
        val updatedAt: Long = 0L,
        val files: Map<String, String> = emptyMap(),
        val chunks: List<Chunk> = emptyList()
    )

    data class Hit(val chunk: Chunk, val score: Double)

    private val root = File(context.filesDir, "agent_rag").apply { mkdirs() }
    private val gson = GsonBuilder().create()

    fun indexProject(projectId: Int?, projectRoot: File): Index = synchronized(this) {
        val old = load(projectId)
        val currentFiles = linkedMapOf<String, String>()
        val chunks = mutableListOf<Chunk>()
        val analyzer = EnhancedCodeAnalyzer()
        val local = LocalModelController(context)
        val files = projectRoot.walkTopDown()
            .filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }
            .filter { it.length() <= 1_500_000L }
            .take(5000)
            .toList()

        for (file in files) {
            val rel = runCatching { file.canonicalFile.relativeTo(projectRoot.canonicalFile).path.replace(File.separatorChar, '/') }.getOrNull() ?: continue
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: continue
            if (looksBinary(bytes, file.extension)) continue
            val hash = sha256(bytes)
            currentFiles[rel] = hash
            val oldChunks = old.chunks.filter { it.path == rel && it.hash == hash }
            if (oldChunks.isNotEmpty()) {
                chunks += oldChunks
                continue
            }
            val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
            if (text.isBlank()) continue
            val meta = analyzer.analyzeFile(file)
            chunks += chunkFile(rel, meta.language, text, hash, meta.classes.flatMap { listOf(it.name) } + meta.functions.map { it.name })
        }

        val embeddedChunks = mutableListOf<Chunk>()
        chunks.chunked(32).forEach { batch ->
            val missing = batch.filter { it.embedding.isEmpty() }
            val vectors = local.embed(missing.map { "${it.path}\n${it.text.take(6000)}" })
            var vi = 0
            batch.forEach { c ->
                if (c.embedding.isNotEmpty()) embeddedChunks += c
                else {
                    val v = vectors.getOrNull(vi++) ?: emptyList()
                    embeddedChunks += c.copy(embedding = v)
                }
            }
        }
        val index = Index(2, projectId, System.currentTimeMillis(), currentFiles, embeddedChunks)
        atomicWrite(projectId, gson.toJson(index))
        index
    }

    fun load(projectId: Int?): Index {
        val f = file(projectId)
        return if (!f.isFile) Index(projectId = projectId) else runCatching {
            gson.fromJson(f.readText(Charsets.UTF_8), Index::class.java) ?: Index(projectId = projectId)
        }.getOrElse { Index(projectId = projectId) }
    }

    fun search(projectId: Int?, query: String, limit: Int = 12): List<Hit> {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty()) return emptyList()
        val index = load(projectId)
        val lexical = index.chunks.map { it to score(it, qTokens) }
        val queryVector = runCatching { LocalModelController(context).embed(listOf(query)).firstOrNull().orEmpty() }.getOrDefault(emptyList())
        return lexical.asSequence()
            .map { (chunk, lexicalScore) ->
                val semantic = cosine(queryVector, chunk.embedding)
                Hit(chunk, lexicalScore + semantic * 10.0 + symbolBoost(chunk, qTokens))
            }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit.coerceIn(1, 50))
            .toList()
    }

    fun buildContext(projectId: Int?, query: String, limit: Int = 10, maxChars: Int = 28000): String {
        val hits = search(projectId, query, limit)
        if (hits.isEmpty()) return "RAG: sin coincidencias locales para esta consulta."
        return buildString {
            append("RAG LOCAL — evidencia recuperada del proyecto\n")
            hits.forEachIndexed { i, hit ->
                append("\n--- RESULTADO ${i + 1} · score=${"%.3f".format(hit.score)} · ${hit.chunk.path}:${hit.chunk.startLine}-${hit.chunk.endLine} ---\n")
                append(hit.chunk.text.take(5000))
                append('\n')
            }
        }.take(maxChars)
    }

    private fun chunkFile(path: String, language: String, text: String, hash: String, symbols: List<String>): List<Chunk> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val window = when {
            language == "Kotlin" || language == "Java" -> 90
            language == "Gradle" || language == "Python" -> 80
            else -> 70
        }
        val overlap = 15
        val out = mutableListOf<Chunk>()
        var start = 0
        while (start < lines.size) {
            val end = minOf(lines.size, start + window)
            val body = lines.subList(start, end).joinToString("\n").trim()
            if (body.isNotBlank()) {
                val localSymbols = symbols.filter { body.contains(it) }.take(30)
                out += Chunk("${hash.take(12)}-$start", path, language, start + 1, end, body, hash, localSymbols)
            }
            if (end == lines.size) break
            start = end - overlap
        }
        return out
    }

    private fun score(c: Chunk, q: List<String>): Double {
        val path = tokenize(c.path).toSet()
        val body = tokenize(c.text)
        if (body.isEmpty()) return 0.0
        val counts = body.groupingBy { it }.eachCount()
        var score = 0.0
        q.forEach { token ->
            val tf = counts[token] ?: 0
            if (tf > 0) score += 1.0 + kotlin.math.ln(1.0 + tf.toDouble())
            if (token in path) score += 2.5
            if (c.symbols.any { it.equals(token, true) }) score += 2.0
        }
        val density = q.count { it in counts }.toDouble() / q.size
        return score + density * 3.0
    }

    private fun symbolBoost(c: Chunk, q: List<String>): Double = q.count { token ->
        c.symbols.any { it.equals(token, true) }
    } * 4.0

    private fun cosine(a: List<Float>, b: List<Float>): Double {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { val x = a[i].toDouble(); val y = b[i].toDouble(); dot += x * y; na += x * x; nb += y * y }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))
    }

    private fun tokenize(value: String): List<String> = Regex("[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9_.$-]{2,}")
        .findAll(value.lowercase())
        .map { it.value }
        .filterNot { it in STOP }
        .toList()

    private fun looksBinary(bytes: ByteArray, extension: String): Boolean {
        if (extension.lowercase() in setOf("kt", "java", "kts", "gradle", "xml", "json", "md", "txt", "py", "js", "ts", "tsx", "jsx", "yml", "yaml", "toml", "properties", "sh", "sql", "csv")) return false
        val sample = bytes.take(4096)
        return sample.any { it == 0.toByte() }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun file(projectId: Int?): File = File(root, "${projectId ?: "global"}.json")
    private fun atomicWrite(projectId: Int?, content: String) {
        val target = file(projectId)
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content, Charsets.UTF_8)
        if (!tmp.renameTo(target)) { target.delete(); require(tmp.renameTo(target)) { "No se pudo guardar el índice RAG" } }
    }

    companion object {
        private val STOP = setOf("para", "como", "esta", "este", "desde", "sobre", "with", "from", "that", "this", "the", "and", "por", "una", "uno", "los", "las", "del", "con", "que", "de", "en", "a", "y", "or", "is", "to")
    }
}
