package com.tools.maestro.core.security

import java.io.File

/**
 * Central path policy for workspace operations.
 * Every user/AI supplied relative path must pass through this guard.
 */
object PathGuard {
    fun normalize(relativePath: String): String {
        val raw = relativePath.trim().replace('\\', '/')
        require(raw.isNotBlank()) { "La ruta está vacía." }
        require(!raw.startsWith("/")) { "No se permiten rutas absolutas." }
        require(!raw.split('/').any { it == ".." }) { "Ruta fuera del proyecto." }
        val clean = raw.split('/').filter { it.isNotBlank() && it != "." }.joinToString("/")
        require(clean.isNotBlank()) { "Ruta inválida." }
        return clean
    }

    fun resolve(root: File, relativePath: String): File {
        val clean = normalize(relativePath)
        val canonicalRoot = root.canonicalFile
        val target = File(canonicalRoot, clean).canonicalFile
        require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) {
            "Ruta fuera del proyecto."
        }
        return target
    }
}
