package com.tools.maestro.data.repository

import com.tools.maestro.data.local.dao.ProjectDao
import com.tools.maestro.data.local.entity.ProjectEntity
import com.tools.maestro.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository pattern implementation for project data access.
 * Abstracts local database operations from domain and UI layers.
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {

    /**
     * Get all projects as flow for reactive updates.
     */
    fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects()
            .map { entities -> entities.map { it.toDomain() } }
    }

    /**
     * Get specific project by ID.
     */
    suspend fun getProjectById(projectId: Int): Project? {
        return projectDao.getProjectById(projectId)?.toDomain()
    }

    /**
     * Create new project.
     */
    suspend fun createProject(
        name: String,
        description: String?,
        type: String,
        rootPath: String
    ): Long {
        val entity = ProjectEntity(
            name = name,
            description = description,
            type = type,
            rootPath = rootPath
        )
        return projectDao.insertProject(entity)
    }

    /**
     * Update existing project.
     */
    suspend fun updateProject(project: Project) {
        projectDao.updateProject(project.toEntity())
    }

    /**
     * Delete project (soft delete - marks as inactive).
     */
    suspend fun deleteProject(projectId: Int) {
        projectDao.softDeleteProject(projectId)
    }

    /**
     * Permanent delete project.
     */
    suspend fun permanentlyDeleteProject(projectId: Int) {
        projectDao.hardDeleteProject(projectId)
    }

    /**
     * Get total project count.
     */
    fun getProjectCount(): Flow<Int> {
        return projectDao.getProjectCount()
    }

    /**
     * Conversion from Entity to Domain model.
     */
    private fun ProjectEntity.toDomain(): Project {
        return Project(
            id = id,
            name = name,
            description = description,
            type = type,
            rootPath = rootPath,
            createdAt = createdAt,
            lastModified = lastModified,
            isActive = isActive
        )
    }

    /**
     * Conversion from Domain model to Entity.
     */
    private fun Project.toEntity(): ProjectEntity {
        return ProjectEntity(
            id = id,
            name = name,
            description = description,
            type = type,
            rootPath = rootPath,
            createdAt = createdAt,
            lastModified = lastModified,
            isActive = isActive
        )
    }
}
