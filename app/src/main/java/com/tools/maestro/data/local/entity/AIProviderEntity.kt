package com.tools.maestro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity para almacenar configuración de proveedores de IA (OpenAI, Claude, etc.)
 */
@Entity(tableName = "ai_providers")
data class AIProviderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    val providerName: String,           // "openai", "claude", "gemini", etc.
    val apiKey: String,                 // API key encriptada
    val isActive: Boolean = false,      // Si es el proveedor activo
    val priority: Int = 0,              // Orden de preferencia (menor = más prioritario)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
