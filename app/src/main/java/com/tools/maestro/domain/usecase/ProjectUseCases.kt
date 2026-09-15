package com.tools.maestro.domain.usecase

import com.tools.maestro.data.repository.ProjectRepository
import com.tools.maestro.domain.model.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use cases for project management.
 * Encapsulates business logic for project operations.
 */
@Singleton
class ProjectUseCases @Inject constructor(
    private val repository: ProjectRepository
) {

    /**
     * Get all projects as reactive flow.
     */
    fun getAllProjects(): Flow<List<Project>> {
        return repository.getAllProjects()
    }

    /**
     * Get specific project by ID.
     */
    suspend fun getProjectById(projectId: Int): Project? {
        return repository.getProjectById(projectId)
    }

    /**
     * Create new project with validation.
     */
    suspend fun createProject(
        name: String,
        description: String?,
        type: String,
        rootPath: String
    ): Result<Long> {
        return try {
            validateProjectInput(name, type, rootPath)
            val projectId = repository.createProject(name, description, type, rootPath)
            Result.success(projectId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update project information.
     */
    suspend fun updateProject(project: Project): Result<Unit> {
        return try {
            validateProject(project)
            repository.updateProject(project)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete project (soft delete).
     */
    suspend fun deleteProject(projectId: Int): Result<Unit> {
        return try {
            repository.deleteProject(projectId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently delete project.
     */
    suspend fun permanentlyDeleteProject(projectId: Int): Result<Unit> {
        return try {
            repository.permanentlyDeleteProject(projectId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get project count as flow.
     */
    fun getProjectCount(): Flow<Int> {
        return repository.getProjectCount()
    }

    /**
     * Validate project input before creation.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private fun validateProjectInput(name: String, type: String, rootPath: String) {
        if (name.isBlank()) {
            throw IllegalArgumentException("Project name cannot be empty")
        }
        if (name.length > 255) {
            throw IllegalArgumentException("Project name too long (max 255 chars)")
        }
        if (type.isBlank()) {
            throw IllegalArgumentException("Project type must be specified")
        }
        if (rootPath.isBlank()) {
            throw IllegalArgumentException("Project path cannot be empty")
        }

        val validTypes = listOf("ANDROID", "KOTLIN", "JAVA", "FLUTTER", "PYTHON", "CPP", "RUST", "IMPORTED")
        if (type !in validTypes) {
            throw IllegalArgumentException("Invalid project type: $type")
        }
    }

    /**
     * Validate existing project.
     */
    private fun validateProject(project: Project) {
        if (project.id <= 0) {
            throw IllegalArgumentException("Invalid project ID")
        }
        validateProjectInput(project.name, project.type, project.rootPath)
    }
}
