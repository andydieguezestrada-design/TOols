package com.tools.maestro.domain.model

/**
 * Domain model for development project.
 */
data class Project(
    val id: Int = 0,
    val name: String,
    val description: String? = null,
    val type: String, // KOTLIN, JAVA, FLUTTER, PYTHON, etc
    val rootPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    val displayName: String get() = name
    val typeIcon: String get() = when (type) {
        "KOTLIN" -> "🔷"
        "JAVA" -> "☕"
        "FLUTTER" -> "🦋"
        "PYTHON" -> "🐍"
        "CPP" -> "⚙️"
        "ANDROID" -> "📱"
        "RUST" -> "🦀"
        else -> "📦"
    }
}

/**
 * Domain model for source files.
 */
data class SourceFile(
    val id: Int = 0,
    val projectId: Int,
    val name: String,
    val path: String,
    val language: String,
    val content: String,
    val size: Long,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * Domain model for build results.
 */
data class BuildResult(
    val id: Int = 0,
    val projectId: Int,
    val status: BuildStatus,
    val startTime: Long,
    val endTime: Long? = null,
    val buildType: BuildType,
    val logs: String,
    val outputPath: String? = null
) {
    val duration: Long? get() = if (endTime != null) endTime - startTime else null
    val isSuccessful: Boolean get() = status == BuildStatus.SUCCESS
}

/**
 * Build status enumeration.
 */
enum class BuildStatus {
    SUCCESS,
    FAILED,
    IN_PROGRESS,
    CANCELLED
}

/**
 * Build type enumeration.
 */
enum class BuildType {
    DEBUG,
    RELEASE,
    PROFILE
}

/**
 * Domain model for AI chat message.
 */
data class ChatMessage(
    val id: Int = 0,
    val projectId: Int? = null,
    val role: ChatRole,
    val content: String,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Chat message role enumeration.
 */
enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Domain model for configured AI provider.
 */
data class AIProvider(
    val id: Int = 0,
    val name: String,
    val type: AIProviderType,
    val endpoint: String,
    val model: String,
    val apiKey: String,
    val isActive: Boolean = false,
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * AI provider type enumeration.
 */
enum class AIProviderType {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    CUSTOM
}

/**
 * Dashboard state with aggregated project information.
 */
data class DashboardState(
    val totalProjects: Int = 0,
    val projects: List<Project> = emptyList(),
    val recentBuilds: List<BuildResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
