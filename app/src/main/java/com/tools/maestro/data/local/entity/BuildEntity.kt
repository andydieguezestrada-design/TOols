package com.tools.maestro.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity para almacenar información de builds del proyecto
 */
@Entity(
    tableName = "builds",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId")  // Índice para mejorar queries que filtran por projectId
    ]
)
data class BuildEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val projectId: Int,                 // Referencia al proyecto
    val buildName: String,              // Nombre del build (Debug, Release, etc.)
    val status: String,                 // "success", "failed", "pending"
    val buildTime: Long = 0,            // Tiempo en segundos
    val buildOutput: String = "",       // Log de salida del build
    val apkPath: String = "",           // Ruta del APK generado
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
