package com.tools.maestro.agent

import java.io.File

/**
 * Motor avanzado de validación que verifica cambios antes de aplicarlos
 * e implementa estrategias de recuperación ante errores.
 */
class AdvancedValidationEngine {
    
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<ValidationError> = emptyList(),
        val warnings: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val score: Float = 0f // 0-1, donde 1 es perfecto
    )
    
    data class ValidationError(
        val type: ErrorType,
        val message: String,
        val severity: Severity = Severity.ERROR,
        val location: String = "",
        val suggestedFix: String = ""
    )
    
    enum class ErrorType {
        SYNTAX_ERROR,
        TYPE_ERROR,
        REFERENCE_ERROR,
        IMPORT_ERROR,
        FILE_NOT_FOUND,
        PERMISSION_ERROR,
        CIRCULAR_DEPENDENCY,
        NAMING_CONVENTION,
        SECURITY_ISSUE,
        PERFORMANCE_ISSUE,
        LOGIC_ERROR,
        UNKNOWN
    }
    
    enum class Severity {
        INFO, WARNING, ERROR, CRITICAL
    }
    
    data class ChangeValidation(
        val proposedChange: ProposedChange,
        val validation: ValidationResult,
        val canApply: Boolean
    )
    
    fun validateProposedChange(
        change: ProposedChange,
        projectMetadata: Map<String, EnhancedCodeAnalyzer.FileMetadata> = emptyMap(),
        projectRoot: File? = null
    ): ChangeValidation {
        val errors = mutableListOf<ValidationError>()
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        
        // Validaciones básicas
        if (change.after.isBlank() && change.kind != ProposedChange.Kind.DELETE) {
            errors.add(ValidationError(
                type = ErrorType.LOGIC_ERROR,
                message = "El contenido del archivo está vacío",
                severity = Severity.WARNING,
                suggestedFix = "Revisar si el contenido fue generado correctamente"
            ))
        }
        
        // Validar según tipo de archivo
        when {
            change.path.endsWith(".kt") -> validateKotlinFile(change, errors, warnings, suggestions)
            change.path.endsWith(".java") -> validateJavaFile(change, errors, warnings, suggestions)
            change.path.endsWith(".gradle") || change.path.endsWith(".gradle.kts") -> 
                validateGradleFile(change, errors, warnings, suggestions)
            change.path.endsWith(".xml") -> validateXmlFile(change, errors, warnings, suggestions)
            change.path.endsWith(".json") -> validateJsonFile(change, errors, warnings, suggestions)
        }
        
        // Validar referencias a otros archivos
        if (projectRoot != null) {
            validateFileReferences(change, projectRoot, errors, warnings, suggestions)
        }
        
        // Validar metadata del proyecto
        validateAgainstMetadata(change, projectMetadata, errors, warnings, suggestions)
        
        // Detectar problemas de seguridad
        validateSecurity(change, errors, warnings, suggestions)
        
        // Detectar problemas de rendimiento
        validatePerformance(change, errors, warnings, suggestions)
        
        val severity = errors.maxOfOrNull { it.severity.ordinal } ?: 2
        val isValid = errors.all { it.severity.ordinal < Severity.ERROR.ordinal }
        val score = calculateValidationScore(errors, warnings)
        
        val validation = ValidationResult(
            isValid = isValid,
            errors = errors,
            warnings = warnings,
            suggestions = suggestions,
            score = score
        )
        
        return ChangeValidation(
            proposedChange = change,
            validation = validation,
            canApply = isValid
        )
    }
    
    private fun validateKotlinFile(
        change: ProposedChange,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Revisar sintaxis básica
        if (content.contains("fun ") && !content.contains("{")) {
            errors.add(ValidationError(
                type = ErrorType.SYNTAX_ERROR,
                message = "Posible función sin cuerpo",
                severity = Severity.ERROR,
                location = change.path
            ))
        }
        
        // Revisar imports sin usar
        val importLines = content.lines().filter { it.trim().startsWith("import ") }
        importLines.forEach { importLine ->
            val importPkg = importLine.substringAfter("import ").trim()
            val className = importPkg.substringAfterLast(".")
            if (!content.contains(className) && !importLine.contains("*")) {
                warnings.add("Import sin usar: $importPkg")
            }
        }
        
        // Revisar convenciones de nombres
        validateNamingConventions(content, "Kotlin", errors, warnings, suggestions)
        
        // Revisar best practices
        if (content.contains("var ") && !content.contains("val ")) {
            suggestions.add("Preferir 'val' sobre 'var' para mejor mantenimiento")
        }
        
        // Revisar null safety
        if (content.contains("!!") && !content.contains("?")) {
            warnings.add("Uso de !! puede causar crashes - considerar usar ?. o ?:")
        }
    }
    
    private fun validateJavaFile(
        change: ProposedChange,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Revisar que tenga declaración de clase
        if (!content.contains("class ") && !content.contains("interface ")) {
            if (!content.contains("package ")) {
                errors.add(ValidationError(
                    type = ErrorType.LOGIC_ERROR,
                    message = "Archivo Java sin declaración de clase o package",
                    severity = Severity.ERROR
                ))
            }
        }
        
        // Revisar acceso sin especificar
        if (content.contains("class ") && !content.contains("public ") && !content.contains("private ")) {
            warnings.add("Especificar modificador de acceso explícitamente")
        }
        
        validateNamingConventions(content, "Java", errors, warnings, suggestions)
    }
    
    private fun validateGradleFile(
        change: ProposedChange,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Revisar que tenga plugins
        if (!content.contains("plugins") && !content.contains("apply ")) {
            warnings.add("Archivo gradle sin aplicar plugins")
        }
        
        // Revisar duplicación de dependencias
        val depPattern = Regex("""implementation\s*\(\s*['"](.*?)['"]\s*\)""")
        val deps = depPattern.findAll(content).map { it.groupValues[1] }.toList()
        deps.groupingBy { it }.eachCount().filter { it.value > 1 }.forEach { (dep, count) ->
            errors.add(ValidationError(
                type = ErrorType.LOGIC_ERROR,
                message = "Dependencia duplicada: $dep (aparece $count veces)",
                severity = Severity.WARNING
            ))
        }
        
        // Revisar versiones
        if (content.contains("implementation") && !content.contains("version")) {
            suggestions.add("Considerar versiones explícitas para dependencias")
        }
    }
    
    private fun validateXmlFile(
        change: ProposedChange,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Revisar XML bien formado
        if (content.contains("<") && !content.contains(">")) {
            errors.add(ValidationError(
                type = ErrorType.SYNTAX_ERROR,
                message = "XML no bien formado",
                severity = Severity.ERROR
            ))
            return
        }
        
        // Contar tags de apertura y cierre
        val openTags = content.split(Regex("<[^/][^>]*>")).size
        val closeTags = content.split(Regex("</[^>]*>")).size
        if (openTags != closeTags) {
            errors.add(ValidationError(
                type = ErrorType.SYNTAX_ERROR,
                message = "Tags desbalanceados en XML",
                severity = Severity.ERROR
            ))
        }
        
        // Revisar indentación
        val lines = content.lines()
        var expectedIndent = 0
        lines.forEach { line ->
            val actualIndent = line.takeWhile { it.isWhitespace() }.length / 2
            if (line.trim().startsWith("</")) expectedIndent--
            if (actualIndent != expectedIndent && line.trim().isNotEmpty()) {
                warnings.add("Posible problema de indentación en: ${line.trim().take(50)}")
            }
            if (line.trim().endsWith(">") && !line.trim().endsWith("/>") && 
                line.trim().startsWith("<") && !line.trim().startsWith("</")) {
                expectedIndent++
            }
        }
    }
    
    private fun validateJsonFile(
        change: ProposedChange,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after.trim()
        
        // Revisar que sea un JSON válido
        try {
            if (!content.startsWith("{") && !content.startsWith("[")) {
                errors.add(ValidationError(
                    type = ErrorType.SYNTAX_ERROR,
                    message = "JSON debe comenzar con { o [",
                    severity = Severity.ERROR
                ))
                return
            }
            
            // Revisar balance de llaves
            var braceCount = 0
            var bracketCount = 0
            content.forEach { char ->
                when (char) {
                    '{' -> braceCount++
                    '}' -> braceCount--
                    '[' -> bracketCount++
                    ']' -> bracketCount--
                }
            }
            
            if (braceCount != 0 || bracketCount != 0) {
                errors.add(ValidationError(
                    type = ErrorType.SYNTAX_ERROR,
                    message = "JSON con llaves o corchetes desbalanceados",
                    severity = Severity.ERROR
                ))
            }
        } catch (e: Exception) {
            errors.add(ValidationError(
                type = ErrorType.SYNTAX_ERROR,
                message = "Error al validar JSON: ${e.message}",
                severity = Severity.ERROR
            ))
        }
    }
    
    private fun validateNamingConventions(
        content: String,
        language: String,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val classPattern = Regex("(class|object)\\s+(\\w+)")
        classPattern.findAll(content).forEach { match ->
            val className = match.groupValues[2]
            if (!className[0].isUpperCase()) {
                warnings.add("Nombre de clase '$className' debe comenzar con mayúscula")
            }
        }
        
        val varPattern = Regex("(val|var)\\s+(\\w+)")
        varPattern.findAll(content).forEach { match ->
            val varName = match.groupValues[2]
            if (varName.contains("_") && !varName.all { it.isUpperCase() || it == '_' }) {
                warnings.add("Nombre de variable '$varName' no sigue convención camelCase")
            }
        }
    }
    
    private fun validateFileReferences(
        change: ProposedChange,
        projectRoot: File,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Buscar referencias a archivos
        val fileRefPattern = Regex("""["']([a-zA-Z0-9/_.-]+)["']""")
        fileRefPattern.findAll(content).forEach { match ->
            val fileRef = match.groupValues[1]
            if (fileRef.contains("/") || fileRef.contains(".")) {
                val refFile = File(projectRoot, fileRef)
                // Solo advertir si referencia claramente no existe
                if (!refFile.exists() && !fileRef.startsWith("http")) {
                    warnings.add("Referencia a archivo que posiblemente no existe: $fileRef")
                }
            }
        }
    }
    
    private fun validateAgainstMetadata(
        change: ProposedChange,
        metadata: Map<String, EnhancedCodeAnalyzer.FileMetadata>,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Revisar imports contra metadata
        val importPattern = Regex("import\\s+([\\w.]+)")
        importPattern.findAll(content).forEach { match ->
            val importPath = match.groupValues[1]
            // Verificar que el import sea válido respecto a los archivos del proyecto
            val localClass = importPath.substringAfterLast(".")
            val exists = metadata.values.any { meta ->
                meta.classes.any { it.name == localClass }
            }
            
            if (!exists && !importPath.startsWith("kotlin.") && !importPath.startsWith("java.")) {
                warnings.add("Import posiblemente inválido: $importPath")
            }
        }
    }
    
    private fun validateSecurity(
        change: ProposedChange,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Detectar credenciales hardcodeadas
        if (content.contains(Regex("""password\s*=\s*["']\w+["']""", RegexOption.IGNORE_CASE))) {
            errors.add(ValidationError(
                type = ErrorType.SECURITY_ISSUE,
                message = "Posible contraseña hardcodeada detectada",
                severity = Severity.CRITICAL,
                suggestedFix = "Usar variables de entorno o configuración segura"
            ))
        }
        
        // Detectar tokens
        if (content.contains(Regex("""token\s*=\s*["'][a-zA-Z0-9_-]{20,}["']""", RegexOption.IGNORE_CASE))) {
            errors.add(ValidationError(
                type = ErrorType.SECURITY_ISSUE,
                message = "Posible token hardcodeado detectado",
                severity = Severity.CRITICAL,
                suggestedFix = "No guardar tokens en código"
            ))
        }
        
        // SQL Injection risk
        if (content.contains(Regex("SELECT.*\\+.*WHERE", RegexOption.IGNORE_CASE))) {
            warnings.add("Posible riesgo de SQL injection detectado")
        }
    }
    
    private fun validatePerformance(
        change: ProposedChange,
        errors: MutableList<ValidationError>,
        warnings: MutableList<String>,
        suggestions: MutableList<String>
    ) {
        val content = change.after
        
        // Detectar operaciones en loop
        if (content.contains(Regex("for\\s*\\(.*\\)\\s*\\{[^}]*new ")) ||
            content.contains(Regex("while\\s*\\([^)]*\\)\\s*\\{[^}]*new "))) {
            suggestions.add("Crear objetos dentro de loops puede afectar rendimiento")
        }
        
        // Detectar queries sin límite
        if (content.contains("SELECT *") && !content.contains("LIMIT")) {
            warnings.add("Query sin LIMIT puede devolver demasiados registros")
        }
        
        // Líneas muy largas
        val longLines = content.lines().filter { it.length > 120 }.count()
        if (longLines > 5) {
            suggestions.add("Múltiples líneas muy largas - considerar refactorizar")
        }
    }
    
    private fun calculateValidationScore(errors: List<ValidationError>, warnings: List<String>): Float {
        val errorScore = errors.fold(1f) { acc, error ->
            acc * (1 - (error.severity.ordinal.toFloat() / Severity.values().size))
        }
        val warningPenalty = (warnings.size * 0.05f).coerceAtMost(0.3f)
        return (errorScore * (1 - warningPenalty)).coerceIn(0f, 1f)
    }
}
