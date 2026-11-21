package com.example.data.system

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.R
import com.example.domain.model.WorkProfileStatus
import com.example.service.EscudoDeviceAdminReceiver
import com.example.service.ShortcutActionActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ActionResult {
    data class Success(val message: String) : ActionResult
    data class Failure(val reason: String) : ActionResult
}

@Singleton
class WorkProfileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dpm: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val userManager: UserManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager
    private val launcherApps: LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val adminComponent: ComponentName =
        ComponentName(context, EscudoDeviceAdminReceiver::class.java)

    fun getStatus(): WorkProfileStatus {
        val isSupported = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
        val isProfileOwner = runCatching { dpm.isProfileOwnerApp(context.packageName) }.getOrDefault(false)
        val isProvisioningAllowed = runCatching {
            dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        }.getOrDefault(false)
        val workProfiles = getWorkUserHandles()
        val restrictionReason = when {
            !isSupported -> "El dispositivo no declara la función android.software.managed_users."
            isProfileOwner -> null
            workProfiles.isNotEmpty() && !isProfileOwner ->
                "Ya existe otro perfil de trabajo activo administrado por otra app. Android solo admite uno."
            !isProvisioningAllowed ->
                "El fabricante o una política existente (Device Owner / límite de usuarios) restringe crear otro perfil de trabajo."
            else -> null
        }

        return WorkProfileStatus(
            isDeviceSupported = isSupported,
            isProvisioningAllowed = isProvisioningAllowed,
            isProfileOwner = isProfileOwner,
            hasWorkProfile = isProfileOwner || workProfiles.isNotEmpty(),
            workUserCount = workProfiles.size,
            restrictionReason = restrictionReason
        )
    }

    fun buildProvisioningIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ALLOW_OFFLINE, true)
            }
        }
    }

    fun getWorkUserHandles(): List<UserHandle> {
        val myHandle = Process.myUserHandle()
        return runCatching {
            userManager.userProfiles.filter { it != myHandle }
        }.getOrDefault(emptyList())
    }

    suspend fun cloneAppToWorkProfile(packageName: String): ActionResult = withContext(Dispatchers.IO) {
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            return@withContext ActionResult.Failure(
                "Escudo no es Profile Owner de este perfil. Ejecuta esta acción desde la instancia de Escudo dentro del perfil de trabajo."
            )
        }
        runCatching {
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.installExistingPackage(adminComponent, packageName)
            } else {
                dpm.enableSystemApp(adminComponent, packageName)
                true
            }
            if (installed) {
                ActionResult.Success("App $packageName clonada/habilitada en el perfil de trabajo.")
            } else {
                dpm.enableSystemApp(adminComponent, packageName)
                ActionResult.Success("Se solicitó habilitar $packageName en el perfil de trabajo.")
            }
        }.getOrElse { err ->
            ActionResult.Failure("No se pudo clonar $packageName: ${err.localizedMessage ?: err.javaClass.simpleName}")
        }
    }

    suspend fun setAppSuspended(packageName: String, suspended: Boolean): ActionResult = withContext(Dispatchers.IO) {
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            return@withContext ActionResult.Failure("Requiere ser Profile Owner del perfil de trabajo.")
        }
        runCatching {
            val failed = dpm.setPackagesSuspended(adminComponent, arrayOf(packageName), suspended)
            if (failed.isEmpty()) {
                val verb = if (suspended) "congelada" else "descongelada"
                ActionResult.Success("$packageName $verb en el perfil de trabajo.")
            } else {
                ActionResult.Failure("Android rechazó suspender ${failed.joinToString()}.")
            }
        }.getOrElse { err ->
            ActionResult.Failure("Error al cambiar suspensión: ${err.localizedMessage}")
        }
    }

    suspend fun setAppHidden(packageName: String, hidden: Boolean): ActionResult = withContext(Dispatchers.IO) {
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            return@withContext ActionResult.Failure("Requiere ser Profile Owner del perfil de trabajo.")
        }
        runCatching {
            val ok = dpm.setApplicationHidden(adminComponent, packageName, hidden)
            if (ok) {
                val verb = if (hidden) "ocultada" else "mostrada"
                ActionResult.Success("$packageName $verb en el perfil de trabajo.")
            } else {
                ActionResult.Failure("No se pudo modificar visibilidad de $packageName.")
            }
        }.getOrElse { err ->
            ActionResult.Failure("Error al ocultar app: ${err.localizedMessage}")
        }
    }

    fun isAppHidden(packageName: String): Boolean {
        if (!dpm.isProfileOwnerApp(context.packageName)) return false
        return runCatching { dpm.isApplicationHidden(adminComponent, packageName) }.getOrDefault(false)
    }

    suspend fun setPermissionGrantState(
        packageName: String,
        permission: String,
        granted: Boolean
    ): ActionResult = withContext(Dispatchers.IO) {
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            return@withContext ActionResult.Failure("Requiere Profile Owner para modificar permisos.")
        }
        runCatching {
            val state = if (granted) {
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            } else {
                DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
            }
            val ok = dpm.setPermissionGrantState(adminComponent, packageName, permission, state)
            if (ok) {
                ActionResult.Success("Permiso actualizado en el perfil de trabajo.")
            } else {
                ActionResult.Failure("El sistema no permitió cambiar $permission para $packageName.")
            }
        }.getOrElse { err ->
            ActionResult.Failure("Error al cambiar permiso: ${err.localizedMessage}")
        }
    }

    suspend fun wipeWorkProfile(): ActionResult = withContext(Dispatchers.IO) {
        if (!dpm.isProfileOwnerApp(context.packageName)) {
            return@withContext ActionResult.Failure(
                "Solo la instancia de Escudo dentro del perfil de trabajo puede eliminar dicho perfil."
            )
        }
        runCatching {
            dpm.wipeData(0)
            ActionResult.Success("Eliminando perfil de trabajo...")
        }.getOrElse { err ->
            ActionResult.Failure("No se pudo eliminar el perfil: ${err.localizedMessage}")
        }
    }

    fun launchWorkProfileApp(packageName: String): ActionResult {
        val workHandle = getWorkUserHandles().firstOrNull() ?: Process.myUserHandle()
        val activities = runCatching { launcherApps.getActivityList(packageName, workHandle) }.getOrNull()
        val firstActivity = activities?.firstOrNull()
        if (firstActivity != null) {
            return runCatching {
                launcherApps.startMainActivity(firstActivity.componentName, workHandle, null, null)
                ActionResult.Success("Abriendo $packageName")
            }.getOrElse { err ->
                ActionResult.Failure("No se pudo lanzar $packageName: ${err.localizedMessage}")
            }
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionResult.Success("Abriendo $packageName")
        } else {
            ActionResult.Failure("La app no tiene actividad principal lanzable o está oculta/congelada.")
        }
    }

    fun createFreezeAndLaunchShortcut(packageName: String, appLabel: String): ActionResult {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return ActionResult.Failure("Tu launcher no admite fijar accesos directos.")
        }
        val shortcutIntent = Intent(context, ShortcutActionActivity::class.java).apply {
            action = ShortcutActionActivity.ACTION_UNFREEZE_AND_LAUNCH
            putExtra(ShortcutActionActivity.EXTRA_PACKAGE_NAME, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "escudo_launch_$packageName")
            .setShortLabel("Abrir $appLabel")
            .setLongLabel("Descongelar y abrir $appLabel con Escudo")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()

        val pinned = ShortcutManagerCompat.requestPinShortcut(
            context,
            shortcut,
            PendingIntent.getActivity(
                context,
                packageName.hashCode(),
                shortcutIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ).intentSender
        )
        return if (pinned) {
            ActionResult.Success("Acceso directo solicitado para $appLabel.")
        } else {
            ActionResult.Failure("No se pudo crear el acceso directo.")
        }
    }
}
