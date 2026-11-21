package com.example.data.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.local.tracker.TrackerScanner
import com.example.domain.model.AppEntry
import com.example.domain.model.PermissionDetail
import com.example.domain.model.ProfileScope
import com.example.domain.model.RiskLevel
import com.example.domain.model.TrackerMatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInspectorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workProfileManager: WorkProfileManager,
    private val trackerScanner: TrackerScanner
) {
    private val pm: PackageManager = context.packageManager
    private val launcherApps: LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val trackerCache = ConcurrentHashMap<String, List<TrackerMatch>>()

    suspend fun loadApps(
        scope: ProfileScope,
        canModifyCurrentScope: Boolean
    ): List<AppEntry> = withContext(Dispatchers.IO) {
        val workPackages = loadWorkProfilePackageSet()
        when (scope) {
            ProfileScope.PRIMARY -> loadPrimaryApps(workPackages, canModifyCurrentScope)
            ProfileScope.WORK -> loadWorkApps(canModifyCurrentScope)
        }
    }

    private fun loadWorkProfilePackageSet(): Set<String> {
        val workHandles = workProfileManager.getWorkUserHandles()
        if (workHandles.isEmpty()) return emptySet()
        val set = HashSet<String>()
        for (handle in workHandles) {
            runCatching {
                launcherApps.getActivityList(null, handle).forEach { activityInfo ->
                    set.add(activityInfo.applicationInfo.packageName)
                }
            }
        }
        return set
    }

    private fun loadPrimaryApps(
        workPackages: Set<String>,
        canModify: Boolean
    ): List<AppEntry> {
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.MATCH_DISABLED_COMPONENTS
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }

        return packages
            .filter { it.packageName != context.packageName }
            .mapNotNull { pkgInfo ->
                buildAppEntry(
                    packageInfo = pkgInfo,
                    scope = ProfileScope.PRIMARY,
                    isInstalledInWork = workPackages.contains(pkgInfo.packageName),
                    canModifyPermissions = canModify
                )
            }
            .sortedWith(compareBy<AppEntry> { it.isSystemApp }.thenBy { it.label.lowercase() })
    }

    private fun loadWorkApps(canModify: Boolean): List<AppEntry> {
        val workHandles = workProfileManager.getWorkUserHandles()
        val isOwner = workProfileManager.getStatus().isProfileOwner
        if (workHandles.isEmpty() && !isOwner) return emptyList()

        if (isOwner) {
            return loadPrimaryApps(emptySet(), canModify = true).map {
                it.copy(
                    profileScope = ProfileScope.WORK,
                    isInstalledInWorkProfile = true,
                    isHidden = workProfileManager.isAppHidden(it.packageName)
                )
            }
        }

        val seen = HashSet<String>()
        val entries = mutableListOf<AppEntry>()
        for (handle in workHandles) {
            val activities = runCatching { launcherApps.getActivityList(null, handle) }.getOrDefault(emptyList())
            for (activity in activities) {
                val appInfo = activity.applicationInfo
                if (!seen.add(appInfo.packageName)) continue
                val pkgInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            appInfo.packageName,
                            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                    }
                }.getOrNull()
                if (pkgInfo != null) {
                    buildAppEntry(
                        packageInfo = pkgInfo,
                        scope = ProfileScope.WORK,
                        isInstalledInWork = true,
                        canModifyPermissions = canModify
                    )?.let { entries.add(it) }
                }
            }
        }
        return entries.sortedBy { it.label.lowercase() }
    }

    private fun buildAppEntry(
        packageInfo: PackageInfo,
        scope: ProfileScope,
        isInstalledInWork: Boolean,
        canModifyPermissions: Boolean
    ): AppEntry? {
        val appInfo = packageInfo.applicationInfo ?: return null
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
            .getOrDefault(packageInfo.packageName)
        val isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { pm.isPackageSuspended(packageInfo.packageName) }.getOrDefault(false)
        } else {
            (appInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        }

        val permissions = extractDangerousPermissions(packageInfo, canModifyPermissions)
        val grantedCount = permissions.count { it.isGranted }
        val cachedTrackers = trackerCache[packageInfo.packageName]
        val riskLevel = computeRiskLevel(grantedCount, cachedTrackers?.size ?: 0)

        return AppEntry(
            packageName = packageInfo.packageName,
            label = label,
            uid = appInfo.uid,
            versionName = packageInfo.versionName ?: "1.0",
            isSystemApp = isSystem,
            profileScope = scope,
            isInstalledInWorkProfile = isInstalledInWork,
            isSuspended = isSuspended,
            isHidden = if (scope == ProfileScope.WORK) workProfileManager.isAppHidden(packageInfo.packageName) else false,
            isEnabled = appInfo.enabled,
            dangerousPermissions = permissions,
            grantedDangerousCount = grantedCount,
            riskLevel = riskLevel,
            sourceApkPath = appInfo.sourceDir,
            trackers = cachedTrackers.orEmpty(),
            trackersScanned = cachedTrackers != null
        )
    }

    private fun extractDangerousPermissions(
        packageInfo: PackageInfo,
        canModify: Boolean
    ): List<PermissionDetail> {
        val requested = packageInfo.requestedPermissions ?: return emptyList()
        val flags = packageInfo.requestedPermissionsFlags ?: IntArray(0)
        val result = mutableListOf<PermissionDetail>()

        requested.forEachIndexed { index, permName ->
            val info = runCatching { pm.getPermissionInfo(permName, 0) }.getOrNull()
            val isDangerous = if (info != null) {
                val baseProtection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.protection
                } else {
                    @Suppress("DEPRECATION")
                    info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
                }
                baseProtection == PermissionInfo.PROTECTION_DANGEROUS
            } else {
                KNOWN_DANGEROUS_PERMISSIONS.containsKey(permName)
            }

            if (isDangerous) {
                val granted = index < flags.size &&
                    (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                val friendlyDesc = KNOWN_DANGEROUS_PERMISSIONS[permName]
                    ?: info?.loadDescription(pm)?.toString()
                    ?: "Permiso de acceso a datos o hardware sensible."

                result.add(
                    PermissionDetail(
                        name = permName,
                        shortName = permName.substringAfterLast('.'),
                        description = friendlyDesc,
                        isDangerous = true,
                        isGranted = granted,
                        canToggleInCurrentScope = canModify
                    )
                )
            }
        }
        return result.sortedByDescending { it.isGranted }
    }

    suspend fun scanTrackersForApp(app: AppEntry): AppEntry = withContext(Dispatchers.IO) {
        val matches = trackerScanner.scanApk(app.sourceApkPath)
        trackerCache[app.packageName] = matches
        app.copy(
            trackers = matches,
            trackersScanned = true,
            riskLevel = computeRiskLevel(app.grantedDangerousCount, matches.size)
        )
    }

    fun getCachedTrackers(packageName: String): List<TrackerMatch>? = trackerCache[packageName]

    fun openSystemAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun launchUninstallApp(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openTrackerWebSearch(
        trackerName: String,
        trackerCompany: String? = null,
        appLabel: String? = null,
        packageName: String? = null
    ): Boolean {
        val query = buildList {
            add("rastreador")
            add(trackerName)
            if (!trackerCompany.isNullOrBlank()) add(trackerCompany)
            if (!appLabel.isNullOrBlank()) add(appLabel)
            if (!packageName.isNullOrBlank()) add(packageName)
        }.joinToString(" ")
        val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openMultipleTrackersWebSearch(
        appLabel: String,
        packageName: String,
        trackerNames: List<String>
    ): Boolean {
        val primaryTracker = trackerNames.take(2).joinToString(" ").ifBlank { "SDK rastreador" }
        return openTrackerWebSearch(
            trackerName = primaryTracker,
            trackerCompany = null,
            appLabel = appLabel,
            packageName = packageName
        )
    }

    private fun computeRiskLevel(grantedDangerous: Int, trackerCount: Int): RiskLevel {
        val score = grantedDangerous * 2 + trackerCount * 2
        return when {
            score >= 8 -> RiskLevel.HIGH
            score >= 4 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    companion object {
        private val KNOWN_DANGEROUS_PERMISSIONS = mapOf(
            Manifest.permission.CAMERA to "Acceso a la cámara y captura de vídeo.",
            Manifest.permission.RECORD_AUDIO to "Grabación de audio desde el micrófono.",
            Manifest.permission.ACCESS_FINE_LOCATION to "Ubicación precisa mediante GPS y Wi-Fi.",
            Manifest.permission.ACCESS_COARSE_LOCATION to "Ubicación aproximada por red.",
            Manifest.permission.ACCESS_BACKGROUND_LOCATION to "Ubicación continua en segundo plano.",
            Manifest.permission.READ_CONTACTS to "Lectura de la agenda de contactos.",
            Manifest.permission.WRITE_CONTACTS to "Modificación de contactos.",
            Manifest.permission.READ_CALL_LOG to "Lectura del historial de llamadas.",
            Manifest.permission.READ_PHONE_STATE to "Lectura de estado e identificadores telefónicos.",
            Manifest.permission.READ_SMS to "Lectura de mensajes SMS.",
            Manifest.permission.SEND_SMS to "Envío de mensajes SMS.",
            Manifest.permission.READ_CALENDAR to "Lectura de eventos del calendario.",
            Manifest.permission.BODY_SENSORS to "Lectura de sensores biométricos/corporales.",
            Manifest.permission.POST_NOTIFICATIONS to "Envío de notificaciones del sistema."
        )
    }
}
