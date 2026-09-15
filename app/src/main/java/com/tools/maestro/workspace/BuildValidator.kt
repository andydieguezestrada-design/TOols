package com.tools.maestro.workspace

import java.io.File

data class ValidationIssue(val severity: Severity, val message: String, val path: String? = null) {
    enum class Severity { ERROR, WARNING }
}

data class WorkspaceValidation(val ok: Boolean, val issues: List<ValidationIssue>)

object BuildValidator {
    fun validate(root: File): WorkspaceValidation {
        if (!root.isDirectory) return WorkspaceValidation(false, listOf(ValidationIssue(ValidationIssue.Severity.ERROR, "La carpeta del proyecto no existe.")))
        val issues = mutableListOf<ValidationIssue>()
        val files = ProjectService.listFiles(root)
        if (files.isEmpty()) issues += ValidationIssue(ValidationIssue.Severity.ERROR, "El proyecto no contiene archivos.")
        if (!File(root, ".github/workflows/build.yml").isFile) {
            issues += ValidationIssue(ValidationIssue.Severity.WARNING, "No existe .github/workflows/build.yml; CI/CD no podrá iniciarse automáticamente.")
        }
        if (files.any { it.length() > 5L * 1024 * 1024 }) {
            issues += ValidationIssue(ValidationIssue.Severity.WARNING, "Hay archivos superiores a 5 MB. Revisa binarios antes de enviarlos al repositorio.")
        }
        files.filter { it.isFile }.forEach { file ->
            if (file.path.contains("${File.separator}.git${File.separator}")) return@forEach
            if (file.name.endsWith(".env") || file.name.contains("secret", true)) {
                issues += ValidationIssue(ValidationIssue.Severity.WARNING, "Posible archivo sensible: ${ProjectService.safeRelative(root, file)}", ProjectService.safeRelative(root, file))
            }
        }
        return WorkspaceValidation(issues.none { it.severity == ValidationIssue.Severity.ERROR }, issues)
    }
}
