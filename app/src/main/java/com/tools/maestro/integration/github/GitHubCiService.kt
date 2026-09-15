package com.tools.maestro.integration.github

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class GitHubRun(val id: Long, val status: String, val conclusion: String?, val htmlUrl: String)
data class GitHubArtifact(val id: Long, val name: String, val sizeInBytes: Long, val expired: Boolean)

class GitHubCiService {
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()

    fun dispatch(token: String, repositoryUrl: String, workflow: String = "build.yml", ref: String = "main") {
        val repo = parseRepository(repositoryUrl)
        val url = "https://api.github.com/repos/$repo/actions/workflows/$workflow/dispatches"
        request(token, url, "POST", """{"ref":"${escape(ref)}"}""")
    }

    fun artifacts(token: String, repositoryUrl: String): List<GitHubArtifact> {
        val repo = parseRepository(repositoryUrl)
        val root = gson.fromJson(request(token, "https://api.github.com/repos/$repo/actions/artifacts?per_page=20", "GET"), JsonObject::class.java)
        val array = root.getAsJsonArray("artifacts") ?: return emptyList()
        return array.map { it.asJsonObject }.map { a ->
            GitHubArtifact(a.get("id").asLong, a.get("name").asString, a.get("size_in_bytes").asLong, a.get("expired").asBoolean)
        }
    }

    fun downloadArtifact(token: String, repositoryUrl: String, artifactId: Long, output: java.io.OutputStream) {
        val repo = parseRepository(repositoryUrl)
        val url = "https://api.github.com/repos/$repo/actions/artifacts/$artifactId/zip"
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub HTTP ${response.code}: ${response.body?.string().orEmpty().take(400)}")
            response.body?.byteStream()?.use { it.copyTo(output) } ?: error("GitHub no devolvió el artefacto.")
        }
    }

    fun waitForLatestRun(token: String, repositoryUrl: String, workflow: String = "build.yml", timeoutSeconds: Long = 600, pollSeconds: Long = 5, onUpdate: ((GitHubRun) -> Unit)? = null): GitHubRun? {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1000
        var last: GitHubRun? = null
        while (System.currentTimeMillis() < deadline) {
            val current = latestRun(token, repositoryUrl, workflow)
            if (current != null) {
                last = current
                onUpdate?.invoke(current)
                if (current.status == "completed") return current
            }
            Thread.sleep(pollSeconds * 1000)
        }
        return last
    }

    fun latestRun(token: String, repositoryUrl: String, workflow: String = "build.yml"): GitHubRun? {
        val repo = parseRepository(repositoryUrl)
        val url = "https://api.github.com/repos/$repo/actions/workflows/$workflow/runs?per_page=1"
        val root = gson.fromJson(request(token, url, "GET"), JsonObject::class.java)
        val runs = root.getAsJsonArray("workflow_runs") ?: return null
        if (runs.size() == 0) return null
        val first = runs.get(0).asJsonObject
        return GitHubRun(
            first.get("id").asLong,
            first.get("status").asString,
            first.get("conclusion")?.takeUnless { it.isJsonNull }?.asString,
            first.get("html_url").asString
        )
    }

    /** Downloads and extracts the textual workflow logs for a run. */
    fun runLogs(token: String, repositoryUrl: String, runId: Long): String {
        val repo = parseRepository(repositoryUrl)
        val url = "https://api.github.com/repos/$repo/actions/runs/$runId/logs"
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub HTTP ${response.code}: ${response.body?.string().orEmpty().take(400)}")
            val bytes = response.body?.bytes() ?: error("GitHub no devolvió logs.")
            val out = StringBuilder()
            java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        out.append("\n===== ").append(entry.name).append(" =====\n")
                        out.append(zip.readBytes().toString(Charsets.UTF_8).take(200_000))
                    }
                }
            }
            return out.toString().takeLast(500_000)
        }
    }

    private fun parseRepository(url: String): String {
        val cleaned = url.trim().removeSuffix("/")
            .removeSuffix(".git")
        val marker = "github.com/"
        val index = cleaned.indexOf(marker)
        require(index >= 0) { "La URL debe ser un repositorio de GitHub." }
        val repo = cleaned.substring(index + marker.length)
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Repositorio GitHub inválido." }
        return repo
    }

    private fun request(token: String, url: String, method: String, body: String = ""): String {
        val builder = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (method == "POST") builder.post(body.toRequestBody("application/json".toMediaType())) else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("GitHub HTTP ${response.code}: ${raw.take(400)}")
            return raw
        }
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
