package com.tools.maestro.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh++ }

    fun granted(permission: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun allFilesGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    fun overlayGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    fun usageGranted(): Boolean = runCatching {
        val ops = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
    fun installGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()


    fun mediaGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
            .all { granted(it) } || (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    } else granted(Manifest.permission.READ_EXTERNAL_STORAGE)

    @Suppress("UNUSED_VARIABLE") val currentRefresh = refresh

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permisos y accesos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Centro de permisos de TOols", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Aquí puedes conceder los accesos que permiten al agente trabajar con archivos, multimedia, entradas del dispositivo, automatización, aplicaciones y tareas en segundo plano. Android exige confirmación del usuario para los accesos sensibles.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item { SectionTitle("ARCHIVOS Y MULTIMEDIA") }
            item {
                PermissionCard({ Icon(Icons.Default.Folder, null) }, "Archivos y almacenamiento compartido",
                    if (allFilesGranted()) "ACCESO COMPLETO ACTIVADO" else "Permite a TOols gestionar archivos y carpetas del almacenamiento compartido.",
                    allFilesGranted(), if (allFilesGranted()) "Gestionar" else "Conceder acceso") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }
            }
            item {
                PermissionCard({ Icon(Icons.Default.Folder, null) }, "Fotos, vídeos y audio",
                    if (mediaGranted()) "PERMISOS MULTIMEDIA ACTIVADOS" else "Permite importar y analizar imágenes, vídeos y audio.",
                    mediaGranted(), "Solicitar") {
                    val permissions = when {
                        Build.VERSION.SDK_INT >= 34 -> arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                        )
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO
                        )
                        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    runtimeLauncher.launch(permissions)
                }
            }

            item { SectionTitle("ENTRADA DEL DISPOSITIVO") }
            item { PermissionCard({ Icon(Icons.Default.CameraAlt, null) }, "Cámara", "Captura directa para análisis, documentos y herramientas visuales.", granted(Manifest.permission.CAMERA), "Solicitar") { runtimeLauncher.launch(arrayOf(Manifest.permission.CAMERA)) } }
            item { PermissionCard({ Icon(Icons.Default.Mic, null) }, "Micrófono", "Entrada de voz y funciones de dictado/audio.", granted(Manifest.permission.RECORD_AUDIO), "Solicitar") { runtimeLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) } }
            item {
                val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
                PermissionCard({ Icon(Icons.Default.LocationOn, null) }, "Ubicación", "Disponible para funciones que realmente necesiten ubicación del dispositivo.", location, "Solicitar") {
                    runtimeLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
            item {
                val bluetooth = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT))
                PermissionCard({ Icon(Icons.Default.Bluetooth, null) }, "Bluetooth", "Comunicación con dispositivos Bluetooth compatibles.", bluetooth, "Solicitar") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) runtimeLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE))
                }
            }

            item { SectionTitle("PRODUCTIVIDAD") }
            item {
                val contacts = granted(Manifest.permission.READ_CONTACTS) && granted(Manifest.permission.WRITE_CONTACTS)
                PermissionCard({ Icon(Icons.Default.Contacts, null) }, "Contactos", "Integración opcional con contactos del teléfono.", contacts, "Solicitar") {
                    runtimeLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                }
            }
            item {
                val calendar = granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR)
                PermissionCard({ Icon(Icons.Default.CalendarMonth, null) }, "Calendario", "Crear y consultar eventos cuando una tarea lo requiera.", calendar, "Solicitar") {
                    runtimeLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }
            }
            item {
                val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)
                PermissionCard({ Icon(Icons.Default.Notifications, null) }, "Notificaciones", "Estado de compilaciones, agente y tareas largas.", notifications, "Solicitar") {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runtimeLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }
            }

            item { SectionTitle("AGENTE Y CONTROL DEL DISPOSITIVO") }
            item { PermissionCard({ Icon(Icons.Default.Smartphone, null) }, "Mostrar sobre otras aplicaciones", "Acceso especial para herramientas flotantes y asistencia contextual.", overlayGranted(), "Abrir Ajustes") { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply { data = Uri.parse("package:${context.packageName}") }) } }
            item { PermissionCard({ Icon(Icons.Default.Security, null) }, "Acceso al uso de aplicaciones", "Permite consultar estadísticas de uso cuando una función del agente lo necesite.", usageGranted(), "Abrir Ajustes") { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } }
            item { PermissionCard({ Icon(Icons.Default.Settings, null) }, "Instalar aplicaciones desde TOols", "Permite iniciar la instalación de APK generados por tus proyectos, siempre mediante confirmación de Android.", installGranted(), "Abrir Ajustes") { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = Uri.parse("package:${context.packageName}") }) } }

            item { SectionTitle("ACCESOS DEL SISTEMA") }
            item {
                ListItem(
                    headlineContent = { Text("Internet, red y tareas en segundo plano") },
                    supportingContent = { Text("INTERNET, estado de red, Wake Lock y servicio en primer plano están declarados para IA, GitHub, CI/CD y tareas prolongadas. Android no muestra un diálogo para estos permisos normales.") },
                    leadingContent = { Icon(Icons.Default.Security, null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Todos los permisos de Android") },
                    supportingContent = { Text("Abre la pantalla oficial de Android para revisar y modificar permisos y accesos especiales de TOols.") },
                    leadingContent = { Icon(Icons.Default.Settings, null) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = { TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:${context.packageName}") }) }) { Text("Abrir") } }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Apps instaladas") },
                    supportingContent = { Text("TOols declara capacidad para consultar aplicaciones instaladas para funciones de proyectos y administración. Android/Google Play puede restringir este acceso según la distribución de la APK.") },
                    leadingContent = { Icon(Icons.Default.Smartphone, null) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Importante") },
                    supportingContent = { Text("Tener el permiso declarado no concede acceso automáticamente. Los permisos peligrosos requieren tu aprobación y los accesos especiales requieren activación explícita en Ajustes de Android.") },
                    leadingContent = { Icon(Icons.Default.Info, null) }
                )
            }
        }
    }
}

@Composable private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun PermissionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    granted: Boolean,
    button: String,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(if (granted) "✓" else "!", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
        }
    }
}
