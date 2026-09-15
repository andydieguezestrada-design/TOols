package com.tools.maestro.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tools.maestro.domain.model.Project

private val Lavender = Color(0xFF6C5CE7)
private val SoftLavender = Color(0xFFECE9FF)
private val Ink = Color(0xFF20202A)
private val Muted = Color(0xFF777782)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onProjectSelected: (Project) -> Unit = {},
    onNewProjectClick: () -> Unit = {},
    onProvidersClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onImportProject: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    onOpenFiles: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    if (showCreate) {
        CreateProjectDialog(
            viewModel,
            { showCreate = false; onProjectSelected(it) },
            { showCreate = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(true, onOpenFiles, icon = { Icon(Icons.Default.Folder, null) }, label = { Text("Files") })
                NavigationBarItem(false, onOpenTerminal, icon = { Icon(Icons.Default.Terminal, null) }, label = { Text("Terminal") })
                NavigationBarItem(true, onOpenChat, icon = { Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Lavender, Color(0xFF9C7BFF)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, null, tint = Color.White) } }, label = { })
                NavigationBarItem(false, onProvidersClick, icon = { Icon(Icons.Default.Hub, null) }, label = { Text("AI") })
                NavigationBarItem(false, onSettingsClick, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") })
            }
        }
    ) { pad ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { CircularProgressIndicator(color = Lavender) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MY WORKSPACE", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.SemiBold)
                            Text("TOols", style = MaterialTheme.typography.headlineSmall, color = Ink, fontWeight = FontWeight.Bold)
                        }
                        Surface(shape = CircleShape, color = SoftLavender, modifier = Modifier.size(44.dp).clickable { onSettingsClick() }) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Lavender) }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Tune, "Settings", tint = Muted) }
                    }
                }

                item {
                    HeroCard(onOpenChat = onOpenChat, onNewProject = { showCreate = true })
                }

                item {
                    SectionHeader("QUICK ACTIONS")
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuickAction("New project", Icons.Default.Add, Lavender) { showCreate = true }
                        QuickAction("Import", Icons.Default.FileUpload, Color(0xFF5B8DEF), onImportProject)
                        QuickAction("AI Agent", Icons.Default.AutoAwesome, Color(0xFF9B6BDB), onOpenChat)
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("WORKSPACE HEALTH", style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.SemiBold)
                                    Text("Ready to build", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
                                }
                                Box(Modifier.size(64.dp).clip(CircleShape).background(SoftLavender), Alignment.Center) {
                                    Text("92%", color = Lavender, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            LinearProgressIndicator(
                                progress = .92f,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = Lavender,
                                trackColor = SoftLavender
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Metric("Projects", state.projects.size.toString())
                                Metric("AI ready", "3")
                                Metric("Build", "Stable")
                            }
                        }
                    }
                }

                item { SectionHeader("YOUR PROJECTS", "${state.projects.size} total") }
                if (state.projects.isEmpty()) {
                    item { EmptyProjectsState({ showCreate = true }, onImportProject) }
                } else {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(project, { onProjectSelected(project) }, { viewModel.deleteProject(project.id) })
                    }
                }

                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = SoftLavender.copy(alpha = .72f)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, null, tint = Lavender)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Build smarter", color = Ink, fontWeight = FontWeight.Bold)
                                Text("Let the agent inspect, plan, review and repair your project.", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = Lavender)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(onOpenChat: () -> Unit, onNewProject: () -> Unit) {
    Surface(shape = RoundedCornerShape(30.dp), color = Color.Transparent) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFFF0EEFF), Color(0xFFE9F0FF)))
            ).padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = .75f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Lavender, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("AI POWERED", style = MaterialTheme.typography.labelSmall, color = Lavender, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("BUILD WITHOUT\nLIMITS.", style = MaterialTheme.typography.headlineLarge, color = Ink, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Create, edit, test and ship from one focused workspace.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpenChat, colors = ButtonDefaults.buttonColors(containerColor = Lavender), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Enter AI mode")
                    }
                    OutlinedButton(onClick = onNewProject, shape = RoundedCornerShape(16.dp)) { Text("New project") }
                }
            }
        }
    }
}

@Composable
private fun RowScope.QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(modifier = Modifier.weight(1f).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = .12f)), Alignment.Center) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelSmall, color = Lavender)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit, onDelete: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(SoftLavender), Alignment.Center) {
                Text(project.typeIcon, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(project.description ?: "Ready for your next build", color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                AssistChip(onClick = {}, label = { Text(project.type) }, leadingIcon = { Icon(Icons.Default.Code, null, Modifier.size(15.dp)) })
            }
            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error) }
                Text("OPEN", color = Lavender, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyProjectsState(onCreate: () -> Unit, onImport: () -> Unit) =
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(SoftLavender), Alignment.Center) { Icon(Icons.Default.FolderOpen, null, tint = Lavender, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(12.dp))
            Text("Your workspace is empty", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.Bold)
            Text("Start a project or import one you already have.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = onCreate) { Text("Create") }; OutlinedButton(onClick = onImport) { Text("Import") } }
        }
    }

@Composable
private fun CreateProjectDialog(vm: DashboardViewModel, onCreated: (Project) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ANDROID") }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create project") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Box { OutlinedButton({ expanded = true }) { Text(type) }; DropdownMenu(expanded, { expanded = false }) { listOf("ANDROID","KOTLIN","JAVA","FLUTTER","PYTHON","CPP","RUST").forEach { DropdownMenuItem(text = { Text(it) }, onClick = { type = it; expanded = false }) } } }
        } },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { vm.createProject(name.trim(), desc.trim().takeIf { it.isNotBlank() }, type) { id -> onCreated(Project(id, name.trim(), desc.trim().takeIf { it.isNotBlank() }, type, "")) } }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
