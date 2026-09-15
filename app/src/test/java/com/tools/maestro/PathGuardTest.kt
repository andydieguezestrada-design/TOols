package com.tools.maestro

import com.tools.maestro.core.security.PathGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class PathGuardTest {
    @Test fun normalizesWorkspacePath() {
        assertEquals("src/main/App.kt", PathGuard.normalize("./src/main/App.kt"))
    }

    @Test fun rejectsTraversal() {
        assertThrows(IllegalArgumentException::class.java) { PathGuard.normalize("../secret.txt") }
    }

    @Test fun rejectsAbsolutePath() {
        assertThrows(IllegalArgumentException::class.java) { PathGuard.resolve(File("/tmp/project"), "/etc/passwd") }
    }
}
