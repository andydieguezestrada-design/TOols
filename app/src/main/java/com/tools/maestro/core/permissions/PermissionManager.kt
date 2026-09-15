package com.tools.maestro.core.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import androidx.core.content.ContextCompat
import com.tools.maestro.core.error.TOolsError
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Central permission/access policy for TOols. */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasPermissions(vararg permissions: String): Boolean = permissions.all(::hasPermission)

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun hasOverlayAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }
    }

    fun hasInstallPackagesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()



    fun missingStoragePermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        ).filterNot(::hasPermission)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE).filterNot(::hasPermission)
        else -> emptyList()
    }

    fun missingDeviceInputPermissions(): List<String> = listOf(
        Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
    ).filterNot(::hasPermission)

    fun missingProductivityPermissions(): List<String> = listOf(
        Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR
    ).filterNot(::hasPermission)

    fun missingLocationPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    ).filterNot(::hasPermission)

    fun missingBluetoothPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    ).filterNot(::hasPermission) else emptyList()

    fun requireInternetPermission() = requirePermissions(Manifest.permission.INTERNET)

    fun requireStorageReadPermission() {
        if (!hasAllFilesAccess() && missingStoragePermissions().isNotEmpty()) {
            throw TOolsError.PermissionError("Storage access is not granted")
        }
    }

    fun requireStorageWritePermission() {
        if (!hasAllFilesAccess() && Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && !hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            throw TOolsError.PermissionError("Storage write permission is not granted")
        }
    }

    fun requireFullStorageAccess() {
        if (!hasAllFilesAccess()) throw TOolsError.PermissionError("All files access is not granted")
    }

    fun requireNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requirePermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requirePermissions(vararg permissions: String) {
        val missing = permissions.filterNot(::hasPermission)
        if (missing.isNotEmpty()) {
            val error = "Missing required permissions: ${missing.joinToString()}"
            Timber.e(error)
            throw TOolsError.PermissionError("Missing: ${missing.first()}")
        }
    }

    fun getRequiredPermissionsForFeature(feature: String): Array<String> = when (feature) {
        "storage_read" -> missingStoragePermissions().toTypedArray()
        "storage_write" -> if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE).filterNot(::hasPermission).toTypedArray() else emptyArray()
        "storage_full" -> emptyArray()
        "network" -> arrayOf(Manifest.permission.INTERNET)
        "notifications" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        "camera" -> arrayOf(Manifest.permission.CAMERA).filterNot(::hasPermission).toTypedArray()
        "microphone" -> arrayOf(Manifest.permission.RECORD_AUDIO).filterNot(::hasPermission).toTypedArray()
        "location" -> missingLocationPermissions().toTypedArray()
        "bluetooth" -> missingBluetoothPermissions().toTypedArray()
        "contacts_calendar" -> missingProductivityPermissions().toTypedArray()
        else -> emptyArray()
    }

    fun getMissingPermissions(vararg permissions: String): List<String> = permissions.filterNot(::hasPermission)

    companion object {
        const val STORAGE_ACCESS_FRAMEWORK = "content://com.android.externalstorage.documents"
    }
}
