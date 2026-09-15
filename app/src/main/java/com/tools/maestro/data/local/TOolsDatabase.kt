package com.tools.maestro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tools.maestro.data.local.dao.ProjectDao
import com.tools.maestro.data.local.entity.AIProviderEntity
import com.tools.maestro.data.local.entity.BuildEntity
import com.tools.maestro.data.local.entity.ChatMessageEntity
import com.tools.maestro.data.local.entity.FileEntity
import com.tools.maestro.data.local.entity.ProjectEntity

/**
 * Room database for TOols application.
 * Contains all entities for projects, files, builds, chat, and AI providers.
 */
@Database(
    entities = [
        ProjectEntity::class,
        FileEntity::class,
        BuildEntity::class,
        ChatMessageEntity::class,
        AIProviderEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TOolsDatabase : RoomDatabase() {

    /**
     * Access to project-related database operations.
     */
    abstract fun projectDao(): ProjectDao

    companion object {
        const val DATABASE_NAME = "tools_database"
    }
}
