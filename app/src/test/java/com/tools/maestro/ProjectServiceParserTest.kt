package com.tools.maestro

import com.tools.maestro.workspace.ProjectService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectServiceParserTest {
    @Test fun parsesSafeAiFiles() {
        val result = ProjectService.parseAiFiles(
            "=== FILE: src/App.kt ===\n```kotlin\nfun main() {}\n```\n"
        )
        assertEquals("fun main() {}\n", result["src/App.kt"])
    }

    @Test fun ignoresUnsafeAiFiles() {
        val result = ProjectService.parseAiFiles(
            "=== FILE: ../../secret ===\n```text\nbad\n```\n"
        )
        assertTrue(result.isEmpty())
    }
}
