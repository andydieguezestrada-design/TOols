package com.tools.maestro

import com.tools.maestro.agent.AgentPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AgentPlannerTest {
    @Test fun detectsCreateAndModifyWithoutWriting() {
        val root = Files.createTempDirectory("tools-agent").toFile()
        File(root, "Main.kt").writeText("old")
        val plan = AgentPlanner.fromAiResponse(root, mapOf("Main.kt" to "new", "New.kt" to "hello"))
        assertEquals(2, plan.changes.size)
        assertEquals("new", plan.changes.first { it.path == "Main.kt" }.after)
        assertTrue(plan.changes.any { it.path == "New.kt" && it.kind.name == "CREATE" })
    }
}
