package com.tools.maestro.agent

/** A small, explicit tool protocol for the TOols coding agent. */
sealed interface AgentAction {
    data class ReadFile(val path: String) : AgentAction
    data class WriteFile(val path: String, val content: String) : AgentAction
    data class DeleteFile(val path: String) : AgentAction
    data class Search(val query: String) : AgentAction
    data class RunCommand(val command: String) : AgentAction
}

data class ProposedChange(
    val path: String,
    val before: String,
    val after: String,
    val kind: Kind
) {
    enum class Kind { CREATE, MODIFY, DELETE }
}

data class AgentPlan(
    val summary: String,
    val changes: List<ProposedChange> = emptyList(),
    val commands: List<String> = emptyList()
)
