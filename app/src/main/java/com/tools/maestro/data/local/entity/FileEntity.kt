package com.tools.maestro.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity para almacenar información de archivos en el proyecto
 */
@Entity(
    tableName = "files",
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
data class FileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val projectId: Int,                 // Referencia al proyecto
    val fileName: String,               // Nombre del archivo
    val filePath: String,               // Ruta completa del archivo
    val fileType: String,               // "kotlin", "xml", "gradle", etc.
    val fileSize: Long = 0,             // Tamaño en bytes
    val lastModified: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
