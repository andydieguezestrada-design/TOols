package com.tools.maestro.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tools.maestro.domain.model.DashboardState
import com.tools.maestro.domain.usecase.ProjectUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import com.tools.maestro.workspace.ProjectService
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val projectUseCases: ProjectUseCases,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()

    init { loadProjects() }

    private fun loadProjects() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        try { projectUseCases.getAllProjects().collect { projects -> _uiState.update { it.copy(projects = projects, totalProjects = projects.size, isLoading = false) } } }
        catch (e: Exception) { _uiState.update { it.copy(error = "No se pudieron cargar los proyectos: ${e.message}", isLoading = false) } }
    }

    fun createProject(name: String, description: String?, type: String, onCreated: (Int) -> Unit) = viewModelScope.launch {
        val root = File(context.getExternalFilesDir(null), "TOolsProjects/$name")
        val result = projectUseCases.createProject(name, description, type, root.absolutePath)
        result.onSuccess { id ->
            val projectId = id.toInt()
            val dir = File(context.getExternalFilesDir(null), "TOolsProjects/project_$projectId")
            ProjectService.createScaffold(context, projectId, name, type)
            projectUseCases.updateProject(com.tools.maestro.domain.model.Project(projectId, name, description, type, dir.absolutePath))
            onCreated(projectId)
        }.onFailure { _uiState.update { it.copy(error = it.toString()) } }
    }


    fun importProject(name: String, description: String?, treeUri: android.net.Uri, onImported: (Int) -> Unit) = viewModelScope.launch {
        val root = File(context.getExternalFilesDir(null), "TOolsProjects/import_pending")
        val result = projectUseCases.createProject(name, description, "IMPORTED", root.absolutePath)
        result.onSuccess { id ->
            val projectId = id.toInt()
            val dir = ProjectService.root(context, projectId)
            runCatching {
                ProjectService.importTree(context, treeUri, projectId)
                projectUseCases.updateProject(com.tools.maestro.domain.model.Project(projectId, name, description, "IMPORTED", dir.absolutePath))
                onImported(projectId)
            }.onFailure { e -> _uiState.update { state -> state.copy(error = "No se pudo importar el proyecto: ${e.message}") } }
        }.onFailure { e -> _uiState.update { state -> state.copy(error = "No se pudo crear el proyecto importado: ${e.message}") } }
    }

    fun deleteProject(id: Int) = viewModelScope.launch { projectUseCases.deleteProject(id) }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun refresh() { loadProjects() }
}
