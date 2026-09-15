package com.tools.maestro.workspace

import android.content.Context
import com.tools.maestro.core.security.PathGuard
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectService {
    fun root(context: Context, projectId: Int): File =
        File(context.getExternalFilesDir(null), "TOolsProjects/project_$projectId")

    fun listFiles(root: File): List<File> = if (!root.exists()) emptyList() else
        root.walkTopDown().filter { it.isFile && !it.path.contains("${File.separator}.git${File.separator}") }
            .take(1000).toList()

    fun safeRelative(root: File, file: File): String = file.canonicalFile.relativeTo(root.canonicalFile).path.replace(File.separatorChar, '/')

    fun languageFor(file: File): String = when (file.extension.lowercase()) {
        "kt" -> "kotlin"; "java" -> "java"; "xml" -> "xml"; "gradle", "kts" -> "gradle"
        "dart" -> "dart"; "py" -> "python"; "rs" -> "rust"; "cpp", "cc", "h", "hpp" -> "cpp"
        "json" -> "json"; "md" -> "markdown"; "yaml", "yml" -> "yaml"; else -> "text"
    }

    fun createScaffold(context: Context, id: Int, name: String, type: String) {
        val dir = root(context, id); dir.mkdirs()
        when (type) {
            "ANDROID" -> createAndroid(dir, name)
            "KOTLIN" -> write(dir, "src/main/kotlin/Main.kt", "fun main() {\n    println(\"$name listo\")\n}\n")
            "JAVA" -> write(dir, "src/Main.java", "public class Main { public static void main(String[] args) { System.out.println(\"$name listo\"); } }\n")
            "PYTHON" -> { write(dir, "main.py", "print(\"$name listo\")\n"); write(dir, "requirements.txt", "") }
            "FLUTTER" -> createFlutter(dir, name)
            "CPP" -> write(dir, "main.cpp", "#include <iostream>\nint main(){ std::cout << \"$name listo\\n\"; }\n")
            "RUST" -> write(dir, "src/main.rs", "fn main() { println!(\"$name listo\"); }\n")
        }
        write(dir, "README.md", "# $name\n\nProyecto creado y administrado con TOols.\n")
        write(dir, ".gitignore", "*.apk\n*.aab\n.gradle/\nbuild/\n.idea/\n.DS_Store\n")
    }

    private fun createAndroid(dir: File, name: String) {
        val appId = "com.tools.generated.${slug(name)}"
        write(dir, "settings.gradle.kts", """import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\ndependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\nrootProject.name = \"${slug(name)}\"\ninclude(\":app\")\n""")
        write(dir, "build.gradle.kts", """plugins {\n    id(\"com.android.application\") version \"8.2.0\" apply false\n    id(\"org.jetbrains.kotlin.android\") version \"1.9.22\" apply false\n}\n""")
        write(dir, "gradle.properties", "android.useAndroidX=true\nandroid.nonTransitiveRClass=true\nkotlin.code.style=official\n")
        write(dir, "app/build.gradle.kts", """plugins { id(\"com.android.application\"); id(\"org.jetbrains.kotlin.android\") }\n\nandroid { namespace = \"$appId\"; compileSdk = 34\n    defaultConfig { applicationId = \"$appId\"; minSdk = 24; targetSdk = 34; versionCode = 1; versionName = \"1.0\" }\n    buildFeatures { compose = true }\n    composeOptions { kotlinCompilerExtensionVersion = \"1.5.10\" }\n    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }\n    kotlinOptions { jvmTarget = \"17\" }\n}\ndependencies {\n    implementation(\"androidx.core:core-ktx:1.12.0\")\n    implementation(\"androidx.activity:activity-compose:1.8.2\")\n    implementation(\"androidx.compose.ui:ui:1.6.3\")\n    implementation(\"androidx.compose.material3:material3:1.2.1\")\n}\n""")
        write(dir, "app/src/main/AndroidManifest.xml", """<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application android:theme=\"@style/AppTheme\" android:label=\"$name\"><activity android:name=\".MainActivity\" android:exported=\"true\"><intent-filter><action android:name=\"android.intent.action.MAIN\"/><category android:name=\"android.intent.category.LAUNCHER\"/></intent-filter></activity></application></manifest>\n""")
        write(dir, "app/src/main/res/values/styles.xml", """<resources><style name=\"AppTheme\" parent=\"android:style/Theme.Material.Light.NoActionBar\" /></resources>\n""")
        write(dir, "app/src/main/java/${appId.replace('.', '/')}/MainActivity.kt", """package $appId\n\nimport android.os.Bundle\nimport androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\nimport androidx.compose.material3.*\nimport androidx.compose.runtime.Composable\nimport androidx.compose.ui.Alignment\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.ui.Modifier\n\nclass MainActivity : ComponentActivity() { override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { App() } } }\n@Composable fun App() { MaterialTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(\"$name\") } } }\n""")
        writeWorkflow(dir, "android")
    }

    private fun createFlutter(dir: File, name: String) {
        write(dir, "pubspec.yaml", """name: ${slug(name)}\ndescription: Generated by TOols\npublish_to: 'none'\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\ndependencies:\n  flutter:\n    sdk: flutter\nflutter:\n  uses-material-design: true\n""")
        write(dir, "lib/main.dart", """import 'package:flutter/material.dart';\nvoid main() => runApp(const App());\nclass App extends StatelessWidget { const App({super.key}); @override Widget build(BuildContext context) => MaterialApp(home: Scaffold(appBar: AppBar(title: const Text('$name')), body: const Center(child: Text('Creada con TOols')))); }\n""")
        writeWorkflow(dir, "flutter")
    }

    private fun writeWorkflow(dir: File, kind: String) {
        val body = if (kind == "flutter") """name: Build Flutter\non:\n  workflow_dispatch:\n  push:\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - uses: subosito/flutter-action@v2\n        with:\n          channel: stable\n      - run: flutter pub get\n      - run: flutter build apk --release\n      - uses: actions/upload-artifact@v4\n        with:\n          name: flutter-release-apk\n          path: build/app/outputs/flutter-apk/app-release.apk\n""" else """name: Build Android\non:\n  workflow_dispatch:\n  push:\njobs:\n  build:\n    runs-on: ubuntu-latest\n    steps:\n      - uses: actions/checkout@v4\n      - uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: '17'\n      - uses: android-actions/setup-android@v3\n      - run: gradle --no-daemon :app:assembleDebug :app:assembleRelease\n      - uses: actions/upload-artifact@v4\n        with:\n          name: android-apks\n          path: |\n            app/build/outputs/apk/debug/app-debug.apk\n            app/build/outputs/apk/release/app-release-unsigned.apk\n"""
        write(dir, ".github/workflows/build.yml", body)
    }

    private fun write(root: File, path: String, content: String) { val f = File(root, path); f.parentFile?.mkdirs(); f.writeText(content) }
    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "my_app" }.take(40)

    fun zipProject(root: File, output: File) {
        output.parentFile?.mkdirs()
        ZipOutputStream(output.outputStream().buffered()).use { zos ->
            listFiles(root).forEach { file ->
                val entry = ZipEntry(safeRelative(root, file)); zos.putNextEntry(entry)
                FileInputStream(file).use { it.copyTo(zos) }; zos.closeEntry()
            }
        }
    }

    fun parseAiFiles(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val re = Regex("(?s)===\\s*FILE:\\s*([^=\\n]+?)\\s*===\\s*```[^\\n]*\\n(.*?)```")
        re.findAll(text).forEach { m ->
            val rawPath = m.groupValues[1].trim()
            runCatching { PathGuard.normalize(rawPath) }.onSuccess { clean ->
                result[clean] = m.groupValues[2].trimEnd() + "\n"
            }
        }
        return result
    }

    fun applyFiles(root: File, files: Map<String, String>): List<String> {
        require(root.isDirectory) { "Proyecto no encontrado." }
        val applied = mutableListOf<String>()
        files.forEach { (path, content) ->
            val f = PathGuard.resolve(root, path)
            f.parentFile?.mkdirs()
            f.writeText(content)
            applied += PathGuard.normalize(path)
        }
        return applied
    }

    fun importTree(context: Context, treeUri: android.net.Uri, projectId: Int) {
        val root = root(context, projectId)
        root.mkdirs()
        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
            ?: error("No se pudo abrir la carpeta seleccionada.")
        require(tree.isDirectory) { "La selección no es una carpeta." }
        copyDocumentTree(context, tree, root)
    }

    private fun copyDocumentTree(context: Context, source: androidx.documentfile.provider.DocumentFile, destination: File) {
        source.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val safe = runCatching { PathGuard.normalize(name) }.getOrNull() ?: return@forEach
            val target = File(destination, safe)
            if (child.isDirectory) {
                target.mkdirs()
                copyDocumentTree(context, child, target)
            } else if (child.isFile) {
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    fun createFile(root: File, relativePath: String): File {
        val file = PathGuard.resolve(root, relativePath)
        require(!file.exists()) { "El archivo ya existe." }
        file.parentFile?.mkdirs()
        file.createNewFile()
        return file
    }

    fun deleteFile(root: File, relativePath: String) {
        val file = PathGuard.resolve(root, relativePath)
        require(file != root) { "Operación no válida." }
        require(file.exists()) { "El archivo no existe." }
        require(file.deleteRecursively()) { "No se pudo eliminar." }
    }

    fun renameFile(root: File, relativePath: String, newName: String): File {
        val source = PathGuard.resolve(root, relativePath)
        require(source.exists()) { "El archivo no existe." }
        val cleanName = newName.trim()
        require(cleanName.isNotBlank() && cleanName != "." && cleanName != ".." && !cleanName.contains('/') && !cleanName.contains('\\')) { "Nombre inválido." }
        val target = File(source.parentFile, cleanName).canonicalFile
        require(!target.exists()) { "Ya existe un archivo con ese nombre." }
        require(source.renameTo(target)) { "No se pudo renombrar." }
        return target
    }
}
