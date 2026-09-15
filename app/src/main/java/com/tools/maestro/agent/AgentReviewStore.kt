package com.tools.maestro.agent

/** Process-local handoff between the AI response and the review screen. */
object AgentReviewStore {
    @Volatile var plan: AgentPlan? = null
    fun clear() { plan = null }
}
