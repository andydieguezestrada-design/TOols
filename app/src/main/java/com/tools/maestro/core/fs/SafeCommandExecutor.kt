package com.tools.maestro.core.fs

import android.content.Context
import com.tools.maestro.workspace.ProjectService
import java.util.concurrent.TimeUnit

/**
 * Command execution boundary. Destructive/network-sensitive commands require approval.
 * The UI/agent must explicitly approve a command before execution.
 */
class SafeCommandExecutor(private val context: Context) {
    data class Result(val exitCode: Int, val output: String, val timedOut: Boolean = false)

    fun requiresApproval(command: String): Boolean {
        val c = command.lowercase()
        val dangerous = listOf("rm -rf", "rm -r", "mkfs", "dd if=", "shutdown", "reboot", "git reset --hard", "git clean -fd", "curl", "wget")
        return dangerous.any(c::contains)
    }

    fun execute(projectId: Int, command: String, approved: Boolean = false, timeoutSeconds: Long = 90): Result {
        if (requiresApproval(command) && !approved) {
            return Result(126, "TOols bloqueó este comando: requiere aprobación explícita.\n$command")
        }
        val root = ProjectService.root(context, projectId)
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(root)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return Result(124, text.takeLast(30000), true)
                }
                text.takeLast(30000)
            }
            Result(process.exitValue(), output)
        } catch (e: Exception) {
            Result(1, "${e::class.simpleName}: ${e.message}")
        }
    }
}
