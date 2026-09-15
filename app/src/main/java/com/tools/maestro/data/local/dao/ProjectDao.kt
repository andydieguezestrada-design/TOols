package com.tools.maestro.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tools.maestro.data.local.entity.AIProviderEntity
import com.tools.maestro.data.local.entity.BuildEntity
import com.tools.maestro.data.local.entity.ChatMessageEntity
import com.tools.maestro.data.local.entity.FileEntity
import com.tools.maestro.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Project operations.
 */
@Dao
interface ProjectDao {

    // ============= Project Operations =============

    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Int): ProjectEntity?

    @Query("SELECT * FROM projects WHERE isActive = 1 ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM projects WHERE isActive = 1")
    fun getProjectCount(): Flow<Int>

    // ============= File Operations =============

    @Insert
    suspend fun insertFile(file: FileEntity): Long

    @Update
    suspend fun updateFile(file: FileEntity)

    @Delete
    suspend fun deleteFile(file: FileEntity)

    @Query("SELECT * FROM files WHERE id = :fileId")
    suspend fun getFileById(fileId: Int): FileEntity?

    @Query("SELECT * FROM files WHERE projectId = :projectId ORDER BY fileName")
    fun getProjectFiles(projectId: Int): Flow<List<FileEntity>>

    @Query("DELETE FROM files WHERE projectId = :projectId")
    suspend fun deleteProjectFiles(projectId: Int)

    // ============= Build Operations =============

    @Insert
    suspend fun insertBuild(build: BuildEntity): Long

    @Query("SELECT * FROM builds WHERE id = :buildId")
    suspend fun getBuildById(buildId: Int): BuildEntity?

    @Query("SELECT * FROM builds WHERE projectId = :projectId ORDER BY buildTime DESC")
    fun getProjectBuilds(projectId: Int): Flow<List<BuildEntity>>

    @Query("SELECT * FROM builds WHERE projectId = :projectId AND status = 'SUCCESS' ORDER BY buildTime DESC LIMIT 1")
    suspend fun getLastSuccessfulBuild(projectId: Int): BuildEntity?

    // ============= Chat Operations =============

    @Insert
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun getProjectChatMessages(projectId: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 50): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE projectId = :projectId")
    suspend fun deleteProjectMessages(projectId: Int)

    // ============= AI Provider Operations =============

    @Insert
    suspend fun insertAIProvider(provider: AIProviderEntity): Long

    @Update
    suspend fun updateAIProvider(provider: AIProviderEntity)

    @Delete
    suspend fun deleteAIProvider(provider: AIProviderEntity)

    @Query("SELECT * FROM ai_providers WHERE isActive = 1 ORDER BY priority")
    fun getActiveProviders(): Flow<List<AIProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE id = :providerId")
    suspend fun getProviderById(providerId: Int): AIProviderEntity?

    @Query("SELECT * FROM ai_providers ORDER BY priority")
    fun getAllProviders(): Flow<List<AIProviderEntity>>

    // ============= Batch Operations =============

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun hardDeleteProject(projectId: Int)

    @Query("UPDATE projects SET isActive = 0 WHERE id = :projectId")
    suspend fun softDeleteProject(projectId: Int)
}
