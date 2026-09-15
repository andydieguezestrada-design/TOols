package com.tools.maestro.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity para almacenar mensajes de chat con IA
 */
@Entity(
    tableName = "chat_messages",
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
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val projectId: Int,                 // Referencia al proyecto
    val senderType: String,             // "user" o "ai"
    val senderName: String,             // Nombre del remitente
    val message: String,                // Contenido del mensaje
    val tokens: Int = 0,                // Tokens usados
    val createdAt: Long = System.currentTimeMillis()
)
