package com.tools.maestro.agent

import java.io.File

/**
 * Analizador avanzado de código para extraer metadata, dependencias y patrones.
 * Permite al agente entender mejor la estructura de los proyectos.
 */
class EnhancedCodeAnalyzer {
    
    data class FileMetadata(
        val path: String,
        val type: FileType,
        val language: String,
        val imports: List<String> = emptyList(),
        val classes: List<ClassInfo> = emptyList(),
        val functions: List<FunctionInfo> = emptyList(),
        val dependencies: List<String> = emptyList(),
        val lineCount: Int = 0,
        val complexity: ComplexityMetric = ComplexityMetric()
    )
    
    data class ClassInfo(
        val name: String,
        val type: String, // "class", "interface", "object", "data class"
        val visibility: String = "public",
        val properties: List<String> = emptyList(),
        val methods: List<String> = emptyList()
    )
    
    data class FunctionInfo(
        val name: String,
        val signature: String,
        val returnType: String = "",
        val parameters: List<String> = emptyList(),
        val isAsync: Boolean = false
    )
    
    data class ComplexityMetric(
        val cyclomaticComplexity: Int = 0,
        val nesting: Int = 0,
        val hasErrorHandling: Boolean = false,
        val hasTests: Boolean = false
    )
    
    enum class FileType {
        KOTLIN, JAVA, GRADLE, XML, JSON, TEXT, BINARY, UNKNOWN
    }
    
    private val cache = mutableMapOf<String, FileMetadata>()
    
    fun analyzeFile(file: File): FileMetadata {
        if (cache.containsKey(file.absolutePath)) {
            return cache[file.absolutePath]!!
        }
        
        val type = detectFileType(file)
        if (type == FileType.BINARY || !file.isFile) {
            return FileMetadata(file.absolutePath, type, "")
        }
        
        val content = runCatching { file.readText() }.getOrDefault("")
        val language = getLanguage(type)
        val imports = extractImports(content, type)
        val classes = extractClasses(content, type)
        val functions = extractFunctions(content, type)
        val dependencies = extractDependencies(content, type)
        val lineCount = content.lines().size
        val complexity = analyzeComplexity(content, type)
        
        return FileMetadata(
            path = file.absolutePath,
            type = type,
            language = language,
            imports = imports,
            classes = classes,
            functions = functions,
            dependencies = dependencies,
            lineCount = lineCount,
            complexity = complexity
        ).also { cache[file.absolutePath] = it }
    }
    
    fun analyzeProject(root: File): Map<String, FileMetadata> {
        val results = mutableMapOf<String, FileMetadata>()
        root.walkTopDown().forEach { file ->
            if (file.isFile && !file.path.contains("/.")) {
                runCatching {
                    val metadata = analyzeFile(file)
                    results[file.relativePath(root)] = metadata
                }
            }
        }
        return results
    }
    
    fun findDependencies(metadata: Map<String, FileMetadata>): Map<String, List<String>> {
        val graph = mutableMapOf<String, List<String>>()
        for ((path, meta) in metadata) {
            val deps = mutableListOf<String>()
            for ((other, otherMeta) in metadata) {
                if (path != other) {
                    val fileName = other.substringAfterLast("/").substringBeforeLast(".")
                    if (meta.imports.any { it.contains(fileName, ignoreCase = true) } ||
                        meta.dependencies.any { it.contains(fileName, ignoreCase = true) }) {
                        deps.add(other)
                    }
                }
            }
            graph[path] = deps
        }
        return graph
    }
    
    private fun detectFileType(file: File): FileType {
        return when (file.extension.lowercase()) {
            "kt" -> FileType.KOTLIN
            "java" -> FileType.JAVA
            "gradle", "kts" -> FileType.GRADLE
            "xml" -> FileType.XML
            "json" -> FileType.JSON
            "txt", "md" -> FileType.TEXT
            "png", "jpg", "jpeg", "gif", "webp", "bin", "class", "so" -> FileType.BINARY
            else -> if (file.isFile && file.length() > 1_000_000) FileType.BINARY else FileType.UNKNOWN
        }
    }
    
    private fun getLanguage(type: FileType): String = when (type) {
        FileType.KOTLIN -> "Kotlin"
        FileType.JAVA -> "Java"
        FileType.GRADLE -> "Gradle"
        FileType.XML -> "XML"
        FileType.JSON -> "JSON"
        FileType.TEXT -> "Text"
        else -> "Unknown"
    }
    
    private fun extractImports(content: String, type: FileType): List<String> {
        val imports = mutableListOf<String>()
        val pattern = when (type) {
            FileType.KOTLIN, FileType.JAVA -> Regex("^import\\s+([\\w.]+)", RegexOption.MULTILINE)
            else -> return imports
        }
        
        pattern.findAll(content).forEach { match ->
            match.groupValues.getOrNull(1)?.let { imports.add(it) }
        }
        return imports
    }
    
    private fun extractClasses(content: String, type: FileType): List<ClassInfo> {
        val classes = mutableListOf<ClassInfo>()
        if (type != FileType.KOTLIN && type != FileType.JAVA) return classes
        
        val patterns = listOf(
            Regex("""(class|data class|object|interface)\s+(\w+)\s*[:{(<]"""),
            Regex("""(class|interface)\s+(\w+)""")
        )
        
        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                val classType = match.groupValues.getOrNull(1) ?: return@forEach
                val className = match.groupValues.getOrNull(2) ?: return@forEach
                classes.add(ClassInfo(
                    name = className,
                    type = classType
                ))
            }
        }
        return classes.distinctBy { it.name }
    }
    
    private fun extractFunctions(content: String, type: FileType): List<FunctionInfo> {
        val functions = mutableListOf<FunctionInfo>()
        if (type != FileType.KOTLIN && type != FileType.JAVA) return functions
        
        val pattern = Regex("""(suspend\s+)?fun\s+(\w+)\s*\((.*?)\)\s*:\s*(\w+)?""")
        pattern.findAll(content).forEach { match ->
            val isSuspend = match.groupValues.getOrNull(1)?.isNotBlank() == true
            val funcName = match.groupValues.getOrNull(2) ?: return@forEach
            val params = match.groupValues.getOrNull(3)?.split(",")?.map { it.trim() } ?: emptyList()
            val returnType = match.groupValues.getOrNull(4) ?: "Unit"
            
            functions.add(FunctionInfo(
                name = funcName,
                signature = "${funcName}(${params.joinToString(", ")}): $returnType",
                returnType = returnType,
                parameters = params,
                isAsync = isSuspend
            ))
        }
        return functions
    }
    
    private fun extractDependencies(content: String, type: FileType): List<String> {
        val deps = mutableListOf<String>()
        
        when (type) {
            FileType.GRADLE -> {
                Regex("""implementation\s*\(\s*["']([^"']+)["']\s*\)""").findAll(content).forEach {
                    it.groupValues.getOrNull(1)?.let { dep -> deps.add(dep) }
                }
            }
            FileType.JSON -> {
                Regex(""""dependencies"\s*:\s*\{[^}]*\}""").findAll(content).forEach {
                    Regex(""""([^"]+)"""").findAll(it.value).forEach { m ->
                        m.groupValues.getOrNull(1)?.let { dep -> deps.add(dep) }
                    }
                }
            }
            else -> {}
        }
        
        return deps
    }
    
    private fun analyzeComplexity(content: String, type: FileType): ComplexityMetric {
        if (type != FileType.KOTLIN && type != FileType.JAVA) {
            return ComplexityMetric()
        }
        
        val lines = content.lines()
        val cyclomaticComplexity = (
            content.split(Regex("\\b(if|when|for|while|catch)\\b")).size - 1
        ).coerceAtMost(100)
        
        val nesting = lines.maxOfOrNull { line ->
            line.takeWhile { it == ' ' || it == '\t' }.length / 4
        } ?: 0
        
        val hasErrorHandling = content.contains(Regex("try|catch|finally|Exception"))
        val hasTests = content.contains(Regex("@Test|@DisplayName|fun test"))
        
        return ComplexityMetric(
            cyclomaticComplexity = cyclomaticComplexity,
            nesting = nesting.coerceAtMost(20),
            hasErrorHandling = hasErrorHandling,
            hasTests = hasTests
        )
    }
    
    fun clearCache() {
        cache.clear()
    }
    
    private fun File.relativePath(root: File): String {
        return this.absolutePath.removePrefix(root.absolutePath).removePrefix("/")
    }
}
