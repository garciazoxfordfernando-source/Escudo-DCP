package com.example.ui.viewmodel

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.prefs.SecurityPreferencesRepository
import com.example.data.repository.FirewallRepository
import com.example.data.system.ActionResult
import com.example.data.system.AppInspectorRepository
import com.example.data.system.ShizukuManager
import com.example.data.system.WorkProfileManager
import com.example.domain.model.AppEntry
import com.example.domain.model.PermissionDetail
import com.example.domain.model.ProfileScope
import com.example.domain.model.RiskLevel
import com.example.domain.model.ShizukuState
import com.example.domain.model.ShizukuStatus
import com.example.domain.model.TrackerMatch
import com.example.domain.model.WorkProfileStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppFilter(val label: String) {
    ALL("Todas"),
    USER("Usuario"),
    SYSTEM("Sistema"),
    HIGH_RISK("Alto riesgo"),
    FROZEN("Congeladas"),
    CLONED("Clonadas"),
    TRACKERS("Con rastreadores")
}

@Immutable
data class AppsUiState(
    val selectedScope: ProfileScope = ProfileScope.PRIMARY,
    val searchQuery: String = "",
    val selectedFilter: AppFilter = AppFilter.ALL,
    val isLoading: Boolean = true,
    val allApps: List<AppEntry> = emptyList(),
    val filteredApps: List<AppEntry> = emptyList(),
    val selectedApp: AppEntry? = null,
    val workProfileStatus: WorkProfileStatus = WorkProfileStatus(
        isDeviceSupported = true,
        isProvisioningAllowed = false,
        isProfileOwner = false,
        hasWorkProfile = false,
        workUserCount = 0,
        restrictionReason = null
    ),
    val shizukuState: ShizukuState = ShizukuState(),
    val shizukuModeEnabled: Boolean = true,
    val blockedPackages: Set<String> = emptySet(),
    val pendingTrackerBlockApp: AppEntry? = null,
    val pendingUninstallApp: AppEntry? = null,
    val scanningAppPackage: String? = null,
    val bannerMessage: String? = null
)

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val appInspectorRepository: AppInspectorRepository,
    private val workProfileManager: WorkProfileManager,
    private val shizukuManager: ShizukuManager,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val firewallRepository: FirewallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                shizukuManager.state,
                securityPreferencesRepository.securityConfig,
                firewallRepository.rules
            ) { shizuku, config, rules ->
                val blocked = rules
                    .asSequence()
                    .filter { !it.allowWifi && !it.allowCellular }
                    .map { it.packageName }
                    .toSet()
                Triple(shizuku, config.shizukuModeEnabled, blocked)
            }.collect { (shizuku, shizukuEnabled, blocked) ->
                _uiState.update {
                    it.copy(
                        shizukuState = shizuku,
                        shizukuModeEnabled = shizukuEnabled,
                        blockedPackages = blocked,
                        workProfileStatus = workProfileManager.getStatus()
                    )
                }
            }
        }
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val wpStatus = workProfileManager.getStatus()
            val scope = _uiState.value.selectedScope
            val shizukuActive = _uiState.value.shizukuModeEnabled &&
                shizukuManager.state.value.status == ShizukuStatus.ACTIVE
            val canModify = when (scope) {
                ProfileScope.WORK -> wpStatus.isProfileOwner
                ProfileScope.PRIMARY -> shizukuActive
            }
            val apps = appInspectorRepository.loadApps(scope, canModify)
            if (scope == ProfileScope.PRIMARY && apps.isNotEmpty()) {
                val defaultAllow = !securityPreferencesRepository.securityConfig.first().firewallAutoBlockNewApps
                firewallRepository.syncAppsIntoRules(apps, defaultAllow)
            }
            _uiState.update { state ->
                val updatedSelected = state.selectedApp?.let { current ->
                    apps.find { it.packageName == current.packageName }
                }
                state.copy(
                    isLoading = false,
                    workProfileStatus = wpStatus,
                    allApps = apps,
                    filteredApps = applyFilters(apps, state.searchQuery, state.selectedFilter),
                    selectedApp = updatedSelected
                )
            }
        }
    }

    fun selectScope(scope: ProfileScope) {
        if (_uiState.value.selectedScope == scope) return
        _uiState.update { it.copy(selectedScope = scope, selectedApp = null) }
        refreshApps()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredApps = applyFilters(state.allApps, query, state.selectedFilter)
            )
        }
    }

    fun selectFilter(filter: AppFilter) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                filteredApps = applyFilters(state.allApps, state.searchQuery, filter)
            )
        }
    }

    fun openAppDetail(app: AppEntry) {
        _uiState.update { it.copy(selectedApp = app) }
    }

    fun dismissAppDetail() {
        _uiState.update { it.copy(selectedApp = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun cloneToWorkProfile(packageName: String) {
        viewModelScope.launch {
            val result = workProfileManager.cloneAppToWorkProfile(packageName)
            handleActionResult(result, refresh = true)
        }
    }

    fun toggleSuspended(app: AppEntry) {
        viewModelScope.launch {
            val targetState = !app.isSuspended
            val result = when {
                app.profileScope == ProfileScope.WORK && _uiState.value.workProfileStatus.isProfileOwner -> {
                    workProfileManager.setAppSuspended(app.packageName, targetState)
                }
                canUseShizuku() -> {
                    shizukuManager.setPackageSuspended(app.packageName, targetState)
                }
                else -> ActionResult.Failure(
                    "Acción bloqueada por Android: requiere Perfil de Trabajo (Profile Owner) o modo Shizuku activo."
                )
            }
            handleActionResult(result, refresh = true)
        }
    }

    fun toggleEnabledViaShizuku(app: AppEntry) {
        viewModelScope.launch {
            if (!canUseShizuku()) {
                _uiState.update {
                    it.copy(bannerMessage = "Activa Shizuku en Ajustes para congelar apps (disable-user) en el perfil principal.")
                }
                return@launch
            }
            val result = shizukuManager.setPackageEnabled(app.packageName, !app.isEnabled)
            handleActionResult(result, refresh = true)
        }
    }

    fun toggleHidden(app: AppEntry) {
        viewModelScope.launch {
            val targetHidden = !app.isHidden
            val result = when {
                app.profileScope == ProfileScope.WORK && _uiState.value.workProfileStatus.isProfileOwner -> {
                    workProfileManager.setAppHidden(app.packageName, targetHidden)
                }
                canUseShizuku() -> {
                    shizukuManager.setPackageHidden(app.packageName, targetHidden)
                }
                else -> ActionResult.Failure(
                    "Ocultar apps requiere Perfil de Trabajo (Profile Owner) o Shizuku."
                )
            }
            handleActionResult(result, refresh = true)
        }
    }

    fun togglePermission(app: AppEntry, permission: PermissionDetail) {
        viewModelScope.launch {
            val targetGranted = !permission.isGranted
            val result = when {
                app.profileScope == ProfileScope.WORK && _uiState.value.workProfileStatus.isProfileOwner -> {
                    workProfileManager.setPermissionGrantState(app.packageName, permission.name, targetGranted)
                }
                app.profileScope == ProfileScope.PRIMARY && canUseShizuku() -> {
                    shizukuManager.setPermissionGranted(app.packageName, permission.name, targetGranted)
                }
                else -> ActionResult.Failure(
                    "En el perfil principal sin Shizuku solo puedes consultar permisos. Abre los ajustes del sistema."
                )
            }
            handleActionResult(result, refresh = true)
        }
    }

    fun scanAppTrackers(app: AppEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanningAppPackage = app.packageName) }
            val updated = appInspectorRepository.scanTrackersForApp(app)
            _uiState.update { state ->
                val newList = state.allApps.map { if (it.packageName == updated.packageName) updated else it }
                state.copy(
                    scanningAppPackage = null,
                    allApps = newList,
                    filteredApps = applyFilters(newList, state.searchQuery, state.selectedFilter),
                    selectedApp = if (state.selectedApp?.packageName == updated.packageName) updated else state.selectedApp,
                    bannerMessage = "Escaneo de ${updated.label}: ${updated.trackers.size} SDK(s) detectados."
                )
            }
        }
    }

    fun requestBlockTrackers(app: AppEntry) {
        _uiState.update { it.copy(pendingTrackerBlockApp = app) }
    }

    fun dismissBlockTrackersDialog() {
        _uiState.update { it.copy(pendingTrackerBlockApp = null) }
    }

    fun confirmBlockTrackers(onNeedVpnConsent: (Intent) -> Unit) {
        val app = _uiState.value.pendingTrackerBlockApp ?: return
        _uiState.update { it.copy(pendingTrackerBlockApp = null) }
        viewModelScope.launch {
            firewallRepository.setAppsNetworkAllowed(listOf(app), allowed = false)
            if (!firewallRepository.vpnActive.value) {
                val prepare = firewallRepository.prepareVpnIntent()
                if (prepare != null) {
                    onNeedVpnConsent(prepare)
                } else {
                    firewallRepository.startVpnService()
                }
            }
            _uiState.update {
                it.copy(bannerMessage = "Rastreadores bloqueados en Firewall para ${app.label}.")
            }
        }
    }

    fun unblockTrackers(app: AppEntry) {
        viewModelScope.launch {
            firewallRepository.setAppsNetworkAllowed(listOf(app), allowed = true)
            _uiState.update { it.copy(bannerMessage = "Conectividad restaurada para ${app.label}.") }
        }
    }

    fun startVpnAfterConsent() {
        firewallRepository.startVpnService()
    }

    fun createShortcut(app: AppEntry) {
        val result = workProfileManager.createFreezeAndLaunchShortcut(app.packageName, app.label)
        handleActionResult(result, refresh = false)
    }

    fun launchWorkApp(packageName: String) {
        val result = workProfileManager.launchWorkProfileApp(packageName)
        if (result is ActionResult.Failure) {
            _uiState.update { it.copy(bannerMessage = result.reason) }
        }
    }

    fun openSystemSettings(packageName: String) {
        appInspectorRepository.openSystemAppSettings(packageName)
    }

    fun requestUninstall(app: AppEntry) {
        _uiState.update { it.copy(pendingUninstallApp = app) }
    }

    fun dismissUninstall() {
        _uiState.update { it.copy(pendingUninstallApp = null) }
    }

    fun confirmUninstall() {
        val target = _uiState.value.pendingUninstallApp ?: return
        _uiState.update { it.copy(pendingUninstallApp = null) }
        val launched = appInspectorRepository.launchUninstallApp(target.packageName)
        if (!launched) {
            _uiState.update {
                it.copy(bannerMessage = "No se pudo abrir el desinstalador de Android para ${target.label}.")
            }
        }
    }

    fun verifyTrackerInBrowser(app: AppEntry, tracker: TrackerMatch) {
        val opened = appInspectorRepository.openTrackerWebSearch(
            trackerName = tracker.name,
            trackerCompany = tracker.company,
            appLabel = app.label,
            packageName = app.packageName
        )
        if (!opened) {
            _uiState.update {
                it.copy(bannerMessage = "No se encontró un navegador web para verificar «${tracker.name}».")
            }
        }
    }

    fun verifyAppTrackersInBrowser(app: AppEntry) {
        val opened = appInspectorRepository.openMultipleTrackersWebSearch(
            appLabel = app.label,
            packageName = app.packageName,
            trackerNames = app.trackers.map { it.name }
        )
        if (!opened) {
            _uiState.update {
                it.copy(bannerMessage = "No se encontró un navegador web para verificar los rastreadores de ${app.label}.")
            }
        }
    }

    private fun canUseShizuku(): Boolean {
        return _uiState.value.shizukuModeEnabled &&
            _uiState.value.shizukuState.status == ShizukuStatus.ACTIVE
    }

    private fun handleActionResult(result: ActionResult, refresh: Boolean) {
        val msg = when (result) {
            is ActionResult.Success -> result.message
            is ActionResult.Failure -> result.reason
        }
        _uiState.update { it.copy(bannerMessage = msg) }
        if (refresh && result is ActionResult.Success) {
            refreshApps()
        }
    }

    private fun applyFilters(
        apps: List<AppEntry>,
        query: String,
        filter: AppFilter
    ): List<AppEntry> {
        val normalized = query.trim().lowercase()
        return apps.filter { app ->
            val matchesQuery = normalized.isEmpty() ||
                app.label.lowercase().contains(normalized) ||
                app.packageName.lowercase().contains(normalized)
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
                AppFilter.HIGH_RISK -> app.riskLevel == RiskLevel.HIGH
                AppFilter.FROZEN -> app.isSuspended || !app.isEnabled || app.isHidden
                AppFilter.CLONED -> app.isInstalledInWorkProfile
                AppFilter.TRACKERS -> app.trackers.isNotEmpty()
            }
            matchesQuery && matchesFilter
        }
    }
}
