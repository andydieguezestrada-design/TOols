package com.tools.maestro.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.*
import androidx.navigation.compose.*
import com.tools.maestro.ai.provider.AIProviderManager
import com.tools.maestro.agent.AgentPatchEngine
import com.tools.maestro.agent.AgentEngine
import com.tools.maestro.agent.AgentMemoryStore
import com.tools.maestro.agent.LocalModelController
import com.tools.maestro.agent.AgentKnowledgeStore
import com.tools.maestro.agent.AgentPlanner
import com.tools.maestro.agent.AgentReviewStore
import com.tools.maestro.agent.BuildRepairEngine
import com.tools.maestro.agent.ProposedChange
import com.tools.maestro.agent.DeviceAgent
import com.tools.maestro.core.fs.DeviceFileManager
import com.tools.maestro.workspace.diff.UnifiedDiff
import com.tools.maestro.domain.model.Project
import com.tools.maestro.ui.dashboard.DashboardScreen
import com.tools.maestro.ui.dashboard.DashboardViewModel
import com.tools.maestro.ui.settings.SettingsScreen
import com.tools.maestro.workspace.ProjectSnapshotService
import com.tools.maestro.workspace.BuildValidator
import com.tools.maestro.integration.github.GitHubCiService
import com.tools.maestro.workspace.ProjectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object Editor : Screen("editor/{projectId}")
    data object Terminal : Screen("terminal?projectId={projectId}")
    data object Chat : Screen("chat?projectId={projectId}")
    data object Files : Screen("files")
    data object Review : Screen("review/{projectId}")
    data object GitHub : Screen("github/{projectId}")
    data object Build : Screen("build/{projectId}")
    data object Providers : Screen("providers")
    data object Settings : Screen("settings")
}


/**
 * TOols motion system: subtle, fast transitions designed for a mobile IDE/agent.
 * Navigation and task feedback should feel alive without slowing interaction.
 */
private fun toolsEnter() = slideInHorizontally(
    initialOffsetX = { it / 12 },
    animationSpec = tween(260, easing = FastOutSlowInEasing)
) + fadeIn(tween(220)) + scaleIn(
    initialScale = 0.985f,
    animationSpec = tween(260, easing = FastOutSlowInEasing)
)

private fun toolsExit() = slideOutHorizontally(
    targetOffsetX = { -it / 18 },
    animationSpec = tween(220, easing = FastOutSlowInEasing)
) + fadeOut(tween(180)) + scaleOut(
    targetScale = 0.985f,
    animationSpec = tween(220, easing = FastOutSlowInEasing)
)

@Composable
private fun ToolsAnimatedTaskIndicator(
    active: Boolean,
    label: String = "Trabajando…"
) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(180))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun TOolsNavGraph(navController: NavHostController = rememberNavController()) {
    var importVm: DashboardViewModel? by remember { mutableStateOf<DashboardViewModel?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val vm = importVm
        if (uri != null && vm != null) {
            val context = navController.context
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: "ImportedProject"
            vm.importProject(name, "Proyecto importado con TOols", uri) { id ->
                navController.navigate("editor/$id")
            }
        }
    }
    NavHost(navController, Screen.Dashboard.route) {
        composable(Screen.Dashboard.route) {
            val vm: DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
            LaunchedEffect(vm) { importVm = vm }
            DashboardScreen(
                vm,
                onProjectSelected = { navController.navigate("editor/${it.id}") },
                onProvidersClick = { navController.navigate(Screen.Providers.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onImportProject = { importLauncher.launch(null) },
                onOpenChat = { navController.navigate("chat") },
                onOpenTerminal = { navController.navigate("terminal") },
                onOpenFiles = { navController.navigate(Screen.Files.route) }
            )
        }
        composable(Screen.Editor.route) { WorkspaceScreen(it.arguments?.getString("projectId")?.toIntOrNull(), navController) }
        composable(Screen.Terminal.route, arguments = listOf(navArgument("projectId") { type = NavType.StringType; nullable = true; defaultValue = null })) { TerminalScreen(it.arguments?.getString("projectId")?.toIntOrNull(), navController) }
        composable(Screen.Chat.route, arguments = listOf(navArgument("projectId") { type = NavType.StringType; nullable = true; defaultValue = null })) { ChatScreen(it.arguments?.getString("projectId")?.toIntOrNull(), navController) }
        composable(Screen.Files.route) { DeviceFilesScreen(navController) }
        composable(Screen.Review.route) { AgentReviewScreen(it.arguments?.getString("projectId")?.toIntOrNull(), navController) }
        composable(Screen.GitHub.route) { GitHubScreen(it.arguments?.getString("projectId")?.toIntOrNull(), navController) }
        composable(Screen.Build.route) { BuildScreen(it.arguments?.getString("projectId")?.toIntOrNull(), navController) }
        composable(Screen.Providers.route) { ProvidersScreen { navController.popBackStack() } }
        composable(Screen.Settings.route) { SettingsScreen { navController.popBackStack() } }
    }
}

private fun projectDir(context: Context, id: Int) = ProjectService.root(context, id)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceScreen(projectId: Int?, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember(projectId) { projectId?.let { projectDir(context, it) } }
    var files by remember { mutableStateOf(emptyList<File>()) }
    var selected by remember { mutableStateOf<File?>(null) }
    var content by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var showNew by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var newPath by remember { mutableStateOf("") }
    var showDelete by remember { mutableStateOf(false) }
    var compactTree by remember { mutableStateOf(false) }

    fun refresh() {
        files = root?.let(ProjectService::listFiles) ?: emptyList()
    }
    fun openFile(f: File) {
        selected = f
        scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { f.readText() }.getOrDefault("") }
            content = text
            original = text
            status = "Opened ${ProjectService.safeRelative(root!!, f)}"
        }
    }
    fun saveFile() {
        val f = selected ?: return
        val r = root ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { com.tools.maestro.core.security.PathGuard.resolve(r, ProjectService.safeRelative(r, f)).writeText(content) }
                .onSuccess { withContext(Dispatchers.Main) { original = content; status = "Saved · ${ProjectService.safeRelative(r, f)}"; refresh() } }
                .onFailure { withContext(Dispatchers.Main) { status = "Save failed · ${it.message}" } }
        }
    }

    LaunchedEffect(root) { refresh() }
    val visibleFiles = remember(files, query) {
        if (query.isBlank()) files else files.filter { ProjectService.safeRelative(root!!, it).contains(query, ignoreCase = true) }
    }
    val dirty = selected != null && content != original

    if (showNew) AlertDialog(
        onDismissRequest = { showNew = false },
        title = { Text("Create file") },
        text = { OutlinedTextField(newPath, { newPath = it }, label = { Text("Path · e.g. src/main.kt") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(enabled = newPath.isNotBlank() && root != null, onClick = {
            runCatching { ProjectService.createFile(root!!, newPath.trim()) }
                .onSuccess { f -> showNew = false; newPath = ""; refresh(); openFile(f) }
                .onFailure { status = "Create failed · ${it.message}" }
        }) { Text("Create") } },
        dismissButton = { TextButton({ showNew = false }) { Text("Cancel") } }
    )
    if (showDelete) AlertDialog(
        onDismissRequest = { showDelete = false },
        title = { Text("Delete file?") },
        text = { Text("${selected?.let { ProjectService.safeRelative(root!!, it) } ?: "Selected file"}\n\nThis action cannot be undone from the editor. A previous agent snapshot can still be restored.") },
        confirmButton = { Button(onClick = {
            runCatching { ProjectService.deleteFile(root!!, ProjectService.safeRelative(root!!, selected!!)) }
                .onSuccess { selected = null; content = ""; original = ""; refresh(); status = "File deleted" }
                .onFailure { status = "Delete failed · ${it.message}" }
            showDelete = false
        }) { Text("Delete") } },
        dismissButton = { TextButton({ showDelete = false }) { Text("Cancel") } }
    )

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // IDE command bar
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Workspace", style = MaterialTheme.typography.titleMedium)
                        Text(if (root != null) ProjectService.root(context, projectId!!).name else "No project", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ showSearch = !showSearch }) { Icon(Icons.Default.Search, "Search") }
                    IconButton({ compactTree = !compactTree }) { Icon(if (compactTree) Icons.Default.ViewSidebar else Icons.Default.ViewQuilt, "Toggle explorer") }
                    IconButton({ showNew = true }) { Icon(Icons.Default.Add, "New file") }
                    IconButton(enabled = dirty, onClick = { saveFile() }) { Icon(Icons.Default.Save, "Save") }
                    IconButton({ projectId?.let { nav.navigate("chat?projectId=$it") } }) { Icon(Icons.Default.AutoAwesome, "AI Builder") }
                    IconButton({ projectId?.let { nav.navigate("build/$it") } }) { Icon(Icons.Default.PlayArrow, "Build") }
                    IconButton({ projectId?.let { nav.navigate("github/$it") } }) { Icon(Icons.Default.Cloud, "GitHub") }
                }
            }
            if (showSearch) Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search files…") })
                    TextButton({ query = "" }) { Text("Clear") }
                }
            }

            Row(Modifier.fillMaxSize()) {
                // Project explorer
                if (!compactTree) Surface(Modifier.width(250.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("EXPLORER", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("${visibleFiles.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Divider()
                        LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                            items(visibleFiles, key = { it.absolutePath }) { f ->
                                val active = selected?.absolutePath == f.absolutePath
                                val relative = ProjectService.safeRelative(root!!, f)
                                Surface(color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                                    Row(Modifier.fillMaxWidth().clickable { openFile(f) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(fileIcon(f), null, Modifier.size(17.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(9.dp))
                                        Text(relative, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Editor surface
                Column(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.background)) {
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (selected != null) fileIcon(selected!!) else Icons.Default.InsertDriveFile, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(selected?.let { ProjectService.safeRelative(root!!, it) } ?: "No file selected", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            if (dirty) Text("● Unsaved", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            if (selected != null) IconButton({ showDelete = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.DeleteOutline, "Delete", Modifier.size(18.dp)) }
                        }
                    }
                    if (selected == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Code, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("Workspace ready", style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(5.dp))
                                Text("Select a file from Explorer to start editing.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(18.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button({ showNew = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("New file") }
                                    OutlinedButton({ projectId?.let { nav.navigate("chat?projectId=$it") } }) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Ask AI") }
                                }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            Surface(Modifier.width(46.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                                LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
                                    items(maxOf(1, content.count { it == '\n' } + 1)) { n ->
                                        Text("${n + 1}", Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = content,
                                onValueChange = { content = it },
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, lineHeight = MaterialTheme.typography.bodyMedium.lineHeight),
                                label = { Text("Source") },
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant, focusedBorderColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Circle, null, Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${content.length} chars", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun fileIcon(file: File): androidx.compose.ui.graphics.vector.ImageVector = when (file.extension.lowercase()) {
    "kt", "kts" -> Icons.Default.Code
    "java" -> Icons.Default.Coffee
    "py" -> Icons.Default.Terminal
    "json", "xml", "yaml", "yml" -> Icons.Default.DataObject
    "md", "txt" -> Icons.Default.Description
    "gradle", "properties" -> Icons.Default.Settings
    else -> Icons.Default.InsertDriveFile
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalScreen(projectId: Int?, nav: NavHostController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var command by remember { mutableStateOf("") }; var output by remember { mutableStateOf("TOols Terminal\nDirectorio del proyecto: ${projectId?.let { projectDir(context, it).absolutePath } ?: "-"}\n") }; var running by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Terminal") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text(output, Modifier.weight(1f).fillMaxWidth().padding(8.dp), fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(command, { command = it }, Modifier.weight(1f), label = { Text("comando") }, singleLine = true); Spacer(Modifier.width(8.dp)); Button(enabled = !running && command.isNotBlank(), onClick = { val cmd = command; command = ""; running = true; scope.launch { val r = withContext(Dispatchers.IO) { shell(context, projectId, cmd) }; output = (output + "\n$ $cmd\n" + r).takeLast(20000); running = false } }) { Text(if (running) "…" else "Ejecutar") } }
        }
    }
}
private fun shell(context: Context, id: Int?, command: String): String = try { ProcessBuilder("/system/bin/sh", "-c", command).directory(id?.let { projectDir(context, it) }).redirectErrorStream(true).start().let { p -> val out = p.inputStream.bufferedReader().readText(); p.waitFor(); out }.takeLast(16000) } catch (e: Exception) { "Error: ${e.message}" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(projectId: Int?, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { AIProviderManager(context) }
    val deviceAgent = remember { DeviceAgent() }
    val memoryStore = remember { AgentMemoryStore(context) }
    val root = projectId?.let { projectDir(context, it) }
    var cachedProjectContext by remember(root) { mutableStateOf<String?>(null) }
    var memorySnapshot by remember(projectId) { mutableStateOf<AgentMemoryStore.Snapshot?>(null) }
    LaunchedEffect(projectId) {
        memorySnapshot = withContext(Dispatchers.IO) { memoryStore.load(projectId) }
    }
    LaunchedEffect(root) {
        cachedProjectContext = root?.let { withContext(Dispatchers.IO) { buildContext(it) } }
    }
    data class Attachment(val uri: Uri, val name: String, val mime: String, val size: Long)
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf("TOols IA: chat abierto. Puedes usarlo sin crear un proyecto y adjuntar documentos, imágenes, audio o vídeo compatibles.")) }
    var busy by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(emptyList<Attachment>()) }
    var status by remember { mutableStateOf("") }
    var agentStage by remember { mutableStateOf("") }
    var agentDetail by remember { mutableStateOf("") }
    var pendingCalls by remember { mutableStateOf(emptyList<DeviceAgent.ToolCall>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val added = uris.mapNotNull { uri ->
            runCatching {
                val cr = context.contentResolver
                val name = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name ?: "archivo"
                val mime = cr.getType(uri) ?: "application/octet-stream"
                val size = cr.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                runCatching { cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                Attachment(uri, name, mime, size)
            }.getOrNull()
        }
        attachments = (attachments + added).distinctBy { it.uri }
    }
    if (pendingCalls.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingCalls = emptyList() },
            title = { Text("Aprobación de TOols") },
            text = { Column { Text("La IA quiere realizar estas acciones en el almacenamiento del teléfono:"); Spacer(Modifier.height(8.dp)); pendingCalls.forEach { Text("• ${it.action} ${it.args}", fontFamily = FontFamily.Monospace) } } },
            confirmButton = { Button(onClick = { val calls = pendingCalls; pendingCalls = emptyList(); scope.launch(Dispatchers.IO) { val out = calls.map { deviceAgent.execute(it, true).result.message }; withContext(kotlinx.coroutines.Dispatchers.Main) { messages = messages + out.map { "TOols · $it" } } } }) { Text("Aprobar y ejecutar") } },
            dismissButton = { TextButton(onClick = { pendingCalls = emptyList() }) { Text("Cancelar") } }
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (root != null) "AI Builder · ${root.name}" else "AI Chat · Modo abierto") },
            navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
            actions = { IconButton({ nav.navigate(Screen.Providers.route) }) { Icon(Icons.Default.Settings, null) } }
        )
    }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .navigationBarsPadding()
                .padding(12.dp)
        ) {
            if (root == null) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text("Modo abierto: no necesitas crear ni abrir un proyecto para usar el chatbot, proveedores y adjuntos.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.height(8.dp))
            }
            memorySnapshot?.let { memory ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Memoria persistente recuperada", style = MaterialTheme.typography.labelLarge)
                        if (memory.lastUserRequest.isNotBlank()) {
                            Text("Última tarea: ${memory.lastUserRequest.take(260)}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        if (memory.planSummary.isNotBlank()) {
                            Text("Último plan: ${memory.planSummary.take(220)}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("El agente usará este checkpoint y comprobará el estado real del proyecto antes de continuar.", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(12.dp)) } }
            }
            if (attachments.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(attachments, key = { it.uri.toString() }) { a ->
                        Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (a.mime.startsWith("image/")) Icons.Default.Image else Icons.Default.AttachFile, null)
                                Spacer(Modifier.width(8.dp)); Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { attachments = attachments.filterNot { it.uri == a.uri } }) { Text("Quitar") }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (busy) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(agentStage.ifBlank { "Trabajando…" }, style = MaterialTheme.typography.labelLarge)
                            if (agentDetail.isNotBlank()) Text(agentDetail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    IconButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Icon(Icons.Default.AttachFile, "Adjuntar archivos") }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 150.dp),
                        label = { Text(if (root == null) "Pregunta a TOols" else "Describe la app o cambio") },
                        minLines = 1,
                        maxLines = 6
                    )
                    Spacer(Modifier.width(8.dp))
                Button(enabled = !busy && (input.isNotBlank() || attachments.isNotEmpty()), onClick = {
                    val q = input.trim().ifBlank { "Analiza los archivos adjuntos y explícame qué contienen y qué puedo hacer con ellos." }
                    val files = attachments.toList()
                    input = ""; attachments = emptyList(); busy = true
                    agentStage = "Iniciando"
                    agentDetail = "Preparando análisis…"
                    messages = messages + "Tú: $q" + files.map { "Adjunto: ${it.name}" }
                    scope.launch {
                        val contextText = cachedProjectContext ?: root?.let { withContext(Dispatchers.IO) { buildContext(it) } } ?: "Modo global sin proyecto."
                        val engine = AgentEngine(context)
                        val result = engine.run(
                            userRequest = q,
                            projectId = projectId,
                            attachments = files.map { AIProviderManager.Attachment(it.uri, it.name, it.mime, it.size) },
                            projectContext = contextText,
                            onProgress = { progress ->
                                scope.launch(Dispatchers.Main.immediate) {
                                    agentStage = progress.stage
                                    agentDetail = progress.detail
                                }
                            }
                        )
                        if (result.pendingApprovals.isNotEmpty()) pendingCalls = result.pendingApprovals
                        if (result.plan != null) AgentReviewStore.plan = result.plan
                        messages = messages + "IA: ${result.answer}" + result.toolResults.map { "TOols · $it" } +
                            (if (result.pendingApprovals.isNotEmpty()) listOf("TOols · ${result.pendingApprovals.size} acción(es) requieren aprobación.") else emptyList()) +
                            (if (result.plan != null) listOf("TOols · ${result.plan.summary} Abre Revisión para aplicar los cambios.") else emptyList())
                        status = "Agente: ${result.rounds} ronda(s) · ${result.toolResults.size} herramienta(s) · ${result.elapsedMs} ms."
                        agentStage = "Listo"
                        agentDetail = "Proceso completado"
                        busy = false
                    }
                    }) { Text(if (busy) "…" else "Enviar") }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceFilesScreen(nav: NavHostController) {
    val context = LocalContext.current
    val manager = remember { DeviceFileManager() }
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var hasAccess by remember { mutableStateOf(manager.hasFullAccess()) }
    fun refresh() { scope.launch { output = withContext(Dispatchers.IO) { manager.list(path).message } } }
    Scaffold(topBar = { TopAppBar(title = { Text("Gestor de archivos del dispositivo") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Surface(color = if (hasAccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(if (hasAccess) "Acceso completo al almacenamiento habilitado" else "La IA necesita acceso al almacenamiento para administrar archivos directamente.")
                    if (!hasAccess) { Spacer(Modifier.height(8.dp)); Button(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) }) { Text("Conceder acceso") } }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(path, { path = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ruta relativa a /storage/emulated/0") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { hasAccess = manager.hasFullAccess(); refresh() }, enabled = hasAccess) { Text("Listar") }; OutlinedButton(onClick = { path = ""; refresh() }) { Text("Raíz") } }
            Spacer(Modifier.height(8.dp))
            Text(output, Modifier.fillMaxSize().padding(8.dp), fontFamily = FontFamily.Monospace)
        }
    }
}

private fun buildContext(root: File): String = ProjectService.listFiles(root).filter { it.length() < 16000 }.take(35).joinToString("\n\n") { "=== ${ProjectService.safeRelative(root, it)} ===\n${runCatching { it.readText() }.getOrDefault("[binario]").take(8000)}" }.take(60000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentReviewScreen(projectId: Int?, nav: NavHostController) {
    val context = LocalContext.current
    val root = projectId?.let { projectDir(context, it) }
    val plan = AgentReviewStore.plan
    var status by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Revisión de cambios") },
            navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            if (plan == null || root == null) {
                Text("No hay un conjunto de cambios pendiente.")
            } else {
                Text(plan.summary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(plan.changes, key = { it.path }) { change ->
                        Card(Modifier.fillMaxWidth().clickable { expanded = if (expanded == change.path) null else change.path }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(change.path, fontFamily = FontFamily.Monospace)
                                Text(if (change.kind == ProposedChange.Kind.CREATE) "NUEVO" else "MODIFICADO", style = MaterialTheme.typography.labelSmall)
                                if (expanded == change.path) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(UnifiedDiff.render(change).takeLast(20000), fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = plan.changes.isNotEmpty(), onClick = {
                        runCatching {
                            val snapshotDir = File(context.filesDir, "snapshots/$projectId")
                            ProjectSnapshotService.create(root, snapshotDir, "reviewed_change")
                            AgentPatchEngine(root).apply(plan.changes)
                            AgentReviewStore.clear()
                            status = "Cambios aplicados con snapshot de seguridad. Puedes volver al proyecto."
                        }.onFailure { status = "Bloqueado: ${it.message}" }
                    }) { Text("APROBAR Y APLICAR") }
                    OutlinedButton(onClick = { AgentReviewStore.clear(); nav.popBackStack() }) { Text("Rechazar") }
                }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildScreen(projectId: Int?, nav: NavHostController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val root = projectId?.let { projectDir(context, it) }; var log by remember { mutableStateOf("Build remoto recomendado: TOols genera el workflow de GitHub para compilar el proyecto con un runner completo.\n") }; var busy by remember { mutableStateOf(false) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null && root != null) scope.launch(Dispatchers.IO) { val tmp = File(context.cacheDir, "export.zip"); ProjectService.zipProject(root, tmp); context.contentResolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }; tmp.delete() } }
    Scaffold(topBar = { TopAppBar(title = { Text("Build / APK") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Compilación", style = MaterialTheme.typography.headlineSmall); Text("El teléfono administra el código; GitHub Actions aporta el SDK Android/Flutter completo para producir APKs.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = root != null, onClick = {
                    val validation = root?.let(BuildValidator::validate)
                    log = if (validation?.ok == true) {
                        "VALIDACIÓN OK\n\nEl proyecto está preparado para CI/CD. Workflow: .github/workflows/build.yml\n\nSiguiente paso: sincronizar con GitHub y ejecutar el workflow."
                    } else {
                        "VALIDACIÓN DEL PROYECTO\n\n" + validation?.issues.orEmpty().joinToString("\n") {
                            "[${it.severity}] ${it.message}"
                        }
                    }
                }) { Text("Validar / preparar build") }; Button(onClick = { saveZip.launch("TOols_${projectId ?: 0}_project.zip") }, enabled = root != null && !busy) { Text("Exportar ZIP") } }
            Text(log, fontFamily = FontFamily.Monospace)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitHubScreen(projectId: Int?, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ci = remember { GitHubCiService() }
    val ai = remember { AIProviderManager(context) }
    val deviceAgent = remember { DeviceAgent() }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Conecta un repositorio y ejecuta CI desde aquí.") }
    var busy by remember { mutableStateOf(false) }
    var runText by remember { mutableStateOf("") }
    var artifacts by remember { mutableStateOf(emptyList<com.tools.maestro.integration.github.GitHubArtifact>()) }
    var selectedArtifact by remember { mutableStateOf<com.tools.maestro.integration.github.GitHubArtifact?>(null) }
    var repairAttempt by remember { mutableStateOf(0) }
    val saveArtifact = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val artifact = selectedArtifact
        if (uri != null && artifact != null && token.isNotBlank() && url.isNotBlank()) {
            busy = true
            scope.launch {
                message = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out -> ci.downloadArtifact(token, url, artifact.id, out) }
                            ?: error("No se pudo abrir el destino.")
                        "Artefacto descargado: ${artifact.name}"
                    }.getOrElse { "Descarga: ${it.message}" }
                }
                busy = false
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("GitHub / CI-CD") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("CI/CD Command Center", style = MaterialTheme.typography.headlineSmall) }
            item { Text("TOols puede sincronizar el proyecto, disparar CI, esperar el runner, recuperar artefactos y preparar una reparación asistida por IA.", style = MaterialTheme.typography.bodyMedium) }
            item { OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL HTTPS del repositorio") }, singleLine = true) }
            item { OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("GitHub token") }, singleLine = true) }
            item { Text("El token permanece en memoria durante esta sesión y no se envía al modelo de IA.", style = MaterialTheme.typography.bodySmall) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), enabled = projectId != null && url.isNotBlank() && token.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch { message = withContext(Dispatchers.IO) { syncGit(context, projectId!!, url, token) }; busy = false }
                    }) { Text(if (busy) "Sincronizando…" else "Commit + Push") }
                    OutlinedButton(modifier = Modifier.weight(1f), enabled = url.isNotBlank() && token.isNotBlank() && !busy, onClick = {
                        busy = true
                        scope.launch {
                            message = withContext(Dispatchers.IO) { runCatching { ci.dispatch(token, url); "Workflow manual enviado." }.getOrElse { "CI: ${it.message}" } }
                            busy = false
                        }
                    }) { Text("Ejecutar CI") }
                }
            }
            item {
                Button(modifier = Modifier.fillMaxWidth(), enabled = url.isNotBlank() && token.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val run = ci.latestRun(token, url) ?: return@runCatching Pair("No hay ejecuciones recientes.", emptyList<com.tools.maestro.integration.github.GitHubArtifact>())
                                val text = "Run #${run.id}\nEstado: ${run.status}\nResultado: ${run.conclusion ?: "en curso"}\n${run.htmlUrl}"
                                Pair(text, ci.artifacts(token, url))
                            }.getOrElse { Pair("CI: ${it.message}", emptyList()) }
                        }
                        runText = result.first; artifacts = result.second; busy = false
                    }
                }) { Text("Actualizar estado + artefactos") }
            }
            item {
                Button(modifier = Modifier.fillMaxWidth(), enabled = url.isNotBlank() && token.isNotBlank() && !busy, onClick = {
                    busy = true; runText = "CI en ejecución…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                ci.dispatch(token, url)
                                val run = ci.waitForLatestRun(token, url) { r -> runText = "Run #${r.id} · ${r.status} · ${r.conclusion ?: "en curso"}" }
                                val finalText = if (run == null) "CI no devolvió una ejecución." else "Run #${run.id}\nEstado: ${run.status}\nResultado: ${run.conclusion ?: "en curso"}\n${run.htmlUrl}"
                                Pair(finalText, ci.artifacts(token, url))
                            }.getOrElse { Pair("CI automático: ${it.message}", emptyList()) }
                        }
                        runText = result.first; artifacts = result.second; busy = false
                    }
                }) { Text(if (busy) "Esperando runner…" else "Ejecutar CI + esperar resultado") }
            }
            item {
                val failed = runText.contains("failure", true) || runText.contains("failed", true) || runText.contains("cancelled", true) || runText.contains("error", true)
                Button(modifier = Modifier.fillMaxWidth(), enabled = failed && projectId != null && url.isNotBlank() && token.isNotBlank() && !busy && repairAttempt < 3, onClick = {
                    repairAttempt++
                    busy = true
                    message = "Extrayendo logs reales del runner y preparando diagnóstico IA…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val run = ci.latestRun(token, url) ?: error("No hay ejecución para diagnosticar.")
                                val logs = ci.runLogs(token, url, run.id)
                                val root = projectDir(context, projectId!!)
                                val prompt = BuildRepairEngine.prompt(buildContext(root), logs, repairAttempt)
                                val answer = ai.sendMessage(prompt)
                                val files = ProjectService.parseAiFiles(answer)
                                if (files.isNotEmpty()) {
                                    AgentReviewStore.plan = AgentPlanner.fromAiResponse(root, files)
                                    "Diagnóstico terminado. ${files.size} archivo(s) fueron propuestos para revisión."
                                } else "La IA diagnosticó el fallo pero no propuso archivos. Revisa el diagnóstico en la respuesta del proveedor."
                            }.getOrElse { "Reparación IA: ${it.message}" }
                        }
                        message = result; busy = false
                        if (AgentReviewStore.plan != null) nav.navigate("review/${projectId!!}")
                    }
                }) { Text("Diagnosticar y preparar reparación IA ($repairAttempt/3)") }
            }
            item { if (runText.isNotBlank()) Text(runText, fontFamily = FontFamily.Monospace) }
            if (artifacts.isNotEmpty()) {
                item { Text("Artefactos disponibles", style = MaterialTheme.typography.titleMedium) }
                items(artifacts, key = { it.id }) { artifact ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(artifact.name, fontFamily = FontFamily.Monospace); Text("${artifact.sizeInBytes / 1024} KB${if (artifact.expired) " · expirado" else ""}", style = MaterialTheme.typography.bodySmall) }
                            Button(enabled = !artifact.expired && !busy, onClick = { selectedArtifact = artifact; saveArtifact.launch("${artifact.name}.zip") }) { Text("Guardar") }
                        }
                    }
                }
            }
            item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun syncGit(context: Context, id: Int, url: String, token: String): String = try { val dir = projectDir(context, id); val git = if (File(dir, ".git").exists()) Git.open(dir) else Git.init().setDirectory(dir).call(); git.use { g -> g.remoteList().call().firstOrNull()?.let { } ?: runCatching { g.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(url)).call() }; g.add().addFilepattern(".").call(); g.commit().setMessage("TOols update ${System.currentTimeMillis()}").setAllowEmpty(false).call(); g.push().setRemote("origin").setCredentialsProvider(UsernamePasswordCredentialsProvider("x-access-token", token)).setPushAll().call() }; "Push completado." } catch (e: Exception) { "GitHub: ${e.message}" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProvidersScreen(back: () -> Unit) {
    val context = LocalContext.current; val ai = remember { AIProviderManager(context) }; val local = remember { LocalModelController(context) }; var localStatus by remember { mutableStateOf(local.health()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Proveedores IA") }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, null) } }) }) { pad -> LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp)) {
        item { Text("Router inteligente: usa el proveedor disponible más prioritario. Si aparece 429/cuota, 403, timeout o fallo de red, lo pone temporalmente en espera y cambia automáticamente.", Modifier.padding(8.dp)) }
        item { Text("Estado: ${ai.providerStatus().joinToString("  •  ")}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) }
        item { ProviderForm("Gemini", "gemini", "gemini-3.6-flash", "https://generativelanguage.googleapis.com/v1beta/models", 0, ai, requiresKey = true) }
        item { ProviderForm("Groq", "groq", "openai/gpt-oss-120b", "https://api.groq.com/openai/v1/chat/completions", 1, ai, requiresKey = true) }
        item { ProviderForm("Mistral", "mistral", "mistral-small-latest", "https://api.mistral.ai/v1/chat/completions", 2, ai, requiresKey = true) }
        item { ProviderForm("Motor local / Ollama / llama.cpp", "local", local.profile().model, local.profile().endpoint, 99, ai, requiresKey = false) }
        item {
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Diagnóstico del modelo local", style = MaterialTheme.typography.titleSmall)
                    Text(if (localStatus.online) "ONLINE · ${localStatus.models.joinToString() }" else "OFFLINE · ${localStatus.message}")
                    Button(onClick = { localStatus = local.health() }) { Text("Comprobar motor local") }
                }
            }
        }
        item { Text("El motor local se ha preparado para modelos orientados a código, herramientas, JSON y RAG. Puede ser Ollama o un servidor llama.cpp OpenAI-compatible; llama.cpp ofrece chat, embeddings, tool use y multimodalidad según el modelo/servidor. TOols no intenta saltarse cuotas ni rotar cuentas/claves.", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
    } }
}
@Composable private fun ProviderForm(title: String, id: String, defaultModel: String, defaultEndpoint: String, priority: Int, ai: AIProviderManager, requiresKey: Boolean) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(defaultModel) }
    var endpoint by remember { mutableStateOf(defaultEndpoint) }
    var status by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (requiresKey) OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true)
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Modelo") }, singleLine = true)
            OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("Endpoint") }, singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = (!requiresKey || key.isNotBlank()) && model.isNotBlank() && endpoint.isNotBlank(),
                    onClick = { ai.saveProvider(id, key, model, endpoint, true, priority); key = ""; status = "Guardado de forma segura." }
                ) { Text("Guardar") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !ai.providers().none { it.id == id },
                    onClick = {
                        status = "Probando…"
                        scope.launch { status = ai.testProvider(id).fold({ it }, { "Error: ${it.message}" }) }
                    }
                ) { Text("Probar") }
            }
            if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.primary)
        }
    }
}
