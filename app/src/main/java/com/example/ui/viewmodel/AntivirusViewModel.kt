package com.example.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.av.OpenSourceAvRules
import com.example.data.local.prefs.SecurityPreferencesRepository
import com.example.data.repository.AntivirusRepository
import com.example.data.system.ActionResult
import com.example.data.system.AppInspectorRepository
import com.example.data.system.ShizukuManager
import com.example.data.system.WorkProfileManager
import com.example.domain.model.AppEntry
import com.example.domain.model.AvScanDepth
import com.example.domain.model.AvScanReport
import com.example.domain.model.AvVerdict
import com.example.domain.model.ProfileScope
import com.example.domain.model.SecurityConfigUi
import com.example.domain.model.ShizukuState
import com.example.domain.model.ShizukuStatus
import com.example.domain.model.TrackerMatch
import com.example.domain.model.WorkProfileStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject

enum class AntivirusTab(val label: String) {
    SCANNER("Escáner AV"),
    ADS_AUDIT("Análisis de Ads"),
    ENGINE_MENU("Motor y Menú AV")
}

enum class AvFilter(val label: String) {
    ALL("Todas"),
    THREATS("Virus / Alto riesgo"),
    RISK("Riesgo"),
    ADS("Con Ads"),
    CLEAN("Limpias")
}

@Immutable
data class AntivirusUiState(
    val selectedTab: AntivirusTab = AntivirusTab.SCANNER,
    val selectedFilter: AvFilter = AvFilter.ALL,
    val searchQuery: String = "",
    val isLoadingApps: Boolean = true,
    val isScanning: Boolean = false,
    val isAdsSweepRunning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val currentAppName: String = "",
    val reports: List<AvScanReport> = emptyList(),
    val filteredReports: List<AvScanReport> = emptyList(),
    val unScannedCount: Int = 0,
    val selectedReport: AvScanReport? = null,
    val pendingUninstallReport: AvScanReport? = null,
    val securityConfig: SecurityConfigUi = SecurityConfigUi(),
    val workProfileStatus: WorkProfileStatus = WorkProfileStatus(
        isDeviceSupported = true,
        isProvisioningAllowed = false,
        isProfileOwner = false,
        hasWorkProfile = false,
        workUserCount = 0,
        restrictionReason = null
    ),
    val shizukuState: ShizukuState = ShizukuState(),
    val databaseVersion: String = OpenSourceAvRules.DATABASE_VERSION,
    val ruleCount: Int = OpenSourceAvRules.totalRuleCount(),
    val bannerMessage: String? = null
)

@HiltViewModel
class AntivirusViewModel @Inject constructor(
    private val antivirusRepository: AntivirusRepository,
    private val appInspectorRepository: AppInspectorRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val workProfileManager: WorkProfileManager,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AntivirusUiState())
    val uiState: StateFlow<AntivirusUiState> = _uiState.asStateFlow()

    private var installedAppsCache: List<AppEntry> = emptyList()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                antivirusRepository.cachedReports,
                securityPreferencesRepository.securityConfig,
                shizukuManager.state
            ) { cached, config, shizuku ->
                Triple(cached, config, shizuku)
            }.collect { (cached, config, shizuku) ->
                val filtered = applyFilters(cached, _uiState.value.searchQuery, _uiState.value.selectedFilter)
                val updatedSelected = _uiState.value.selectedReport?.let { sel ->
                    cached.find { it.packageName == sel.packageName } ?: sel
                }
                _uiState.update { state ->
                    state.copy(
                        reports = cached,
                        filteredReports = filtered,
                        selectedReport = updatedSelected,
                        securityConfig = config,
                        shizukuState = shizuku,
                        workProfileStatus = workProfileManager.getStatus(),
                        unScannedCount = (installedAppsCache.size - cached.size).coerceAtLeast(0)
                    )
                }
            }
        }
        loadInventoryAndAutoAdsIfNeeded()
    }

    private fun loadInventoryAndAutoAdsIfNeeded() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            installedAppsCache = appInspectorRepository.loadApps(
                scope = ProfileScope.PRIMARY,
                canModifyCurrentScope = false
            )
            _uiState.update { state ->
                state.copy(
                    isLoadingApps = false,
                    unScannedCount = (installedAppsCache.size - state.reports.size).coerceAtLeast(0)
                )
            }
        }
    }

    fun selectTab(tab: AntivirusTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun selectFilter(filter: AvFilter) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                filteredReports = applyFilters(state.reports, state.searchQuery, filter)
            )
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredReports = applyFilters(state.reports, query, state.selectedFilter)
            )
        }
    }

    fun startFullScan(forceRefresh: Boolean = false) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val config = _uiState.value.securityConfig
            if (installedAppsCache.isEmpty()) {
                installedAppsCache = appInspectorRepository.loadApps(ProfileScope.PRIMARY, false)
            }
            val targets = if (config.avScanSystemApps) {
                installedAppsCache
            } else {
                installedAppsCache.filter { !it.isSystemApp }
            }.ifEmpty { installedAppsCache }

            _uiState.update {
                it.copy(
                    isScanning = true,
                    isAdsSweepRunning = false,
                    scannedCount = 0,
                    totalCount = targets.size,
                    currentAppName = ""
                )
            }

            try {
                for (index in targets.indices) {
                    val app = targets[index]
                    _uiState.update {
                        it.copy(scannedCount = index, currentAppName = app.label)
                    }
                    antivirusRepository.scanApp(
                        app = app,
                        depth = config.avScanDepth,
                        useCache = config.avUseIncrementalCache,
                        includeAdsAudit = config.avAutoAnalyzeAds,
                        forceRefresh = forceRefresh
                    )
                    yield()
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scannedCount = targets.size,
                        currentAppName = "",
                        bannerMessage = "Análisis ${config.avScanDepth.label} completado (${targets.size} apps)."
                    )
                }
            } finally {
                _uiState.update { it.copy(isScanning = false, currentAppName = "") }
            }
        }
    }

    fun runAutoAdsAnalysis() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            if (installedAppsCache.isEmpty()) {
                installedAppsCache = appInspectorRepository.loadApps(ProfileScope.PRIMARY, false)
            }
            val targets = installedAppsCache.filter { !it.isSystemApp }.ifEmpty { installedAppsCache }
            _uiState.update {
                it.copy(
                    selectedTab = AntivirusTab.ADS_AUDIT,
                    isScanning = true,
                    isAdsSweepRunning = true,
                    scannedCount = 0,
                    totalCount = targets.size,
                    currentAppName = ""
                )
            }

            try {
                var appsWithAds = 0
                for (index in targets.indices) {
                    val app = targets[index]
                    _uiState.update { it.copy(scannedCount = index, currentAppName = app.label) }
                    val report = antivirusRepository.analyzeAds(app)
                    if (report != null && report.adSdks.isNotEmpty()) {
                        appsWithAds++
                    }
                    yield()
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        isAdsSweepRunning = false,
                        scannedCount = targets.size,
                        currentAppName = "",
                        bannerMessage = "Auditoría automática de Ads lista: $appsWithAds app(s) con redes publicitarias."
                    )
                }
            } finally {
                _uiState.update {
                    it.copy(isScanning = false, isAdsSweepRunning = false, currentAppName = "")
                }
            }
        }
    }

    fun rescanPackage(packageName: String) {
        viewModelScope.launch {
            val config = _uiState.value.securityConfig
            val inWork = _uiState.value.workProfileStatus.isProfileOwner
            val updated = antivirusRepository.scanPackageName(
                packageName = packageName,
                depth = config.avScanDepth,
                inWorkProfile = inWork,
                useCache = false,
                includeAdsAudit = true,
                forceRefresh = true
            )
            if (updated != null) {
                _uiState.update {
                    it.copy(
                        selectedReport = updated,
                        bannerMessage = "${updated.appLabel}: ${updated.verdict.label} (${updated.threatScore}/100)"
                    )
                }
            }
        }
    }

    fun openOrAnalyzePackage(packageName: String) {
        val existing = _uiState.value.reports.find { it.packageName == packageName }
        if (existing != null) {
            _uiState.update { it.copy(selectedTab = AntivirusTab.SCANNER, selectedReport = existing) }
        } else {
            _uiState.update { it.copy(selectedTab = AntivirusTab.SCANNER) }
        }
        rescanPackage(packageName)
    }

    fun requestUninstall(report: AvScanReport) {
        _uiState.update { it.copy(pendingUninstallReport = report) }
    }

    fun dismissUninstall() {
        _uiState.update { it.copy(pendingUninstallReport = null) }
    }

    fun confirmUninstall() {
        val target = _uiState.value.pendingUninstallReport ?: return
        _uiState.update { it.copy(pendingUninstallReport = null) }
        val launched = appInspectorRepository.launchUninstallApp(target.packageName)
        if (!launched) {
            _uiState.update {
                it.copy(bannerMessage = "No se pudo abrir el desinstalador de Android para ${target.appLabel}.")
            }
        }
    }

    fun verifySdkInBrowser(report: AvScanReport, sdk: TrackerMatch) {
        val opened = appInspectorRepository.openTrackerWebSearch(
            trackerName = sdk.name,
            trackerCompany = sdk.company,
            appLabel = report.appLabel,
            packageName = report.packageName
        )
        if (!opened) {
            _uiState.update {
                it.copy(bannerMessage = "No se encontró un navegador web instalado para buscar «${sdk.name}».")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            it.copy(
                isScanning = false,
                isAdsSweepRunning = false,
                currentAppName = "",
                bannerMessage = "Análisis detenido por el usuario."
            )
        }
    }

    fun openReport(report: AvScanReport) {
        _uiState.update { it.copy(selectedReport = report) }
    }

    fun dismissReport() {
        _uiState.update { it.copy(selectedReport = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun setScanDepth(depth: AvScanDepth) {
        viewModelScope.launch {
            securityPreferencesRepository.setAvScanDepth(depth)
            _uiState.update {
                it.copy(bannerMessage = "Nivel del motor cambiado a ${depth.label}.")
            }
        }
    }

    fun setBatterySaverMode(batterySaver: Boolean) {
        viewModelScope.launch {
            securityPreferencesRepository.setAvBatterySaverMode(batterySaver)
            _uiState.update {
                it.copy(
                    bannerMessage = if (batterySaver) {
                        "Ahorro de batería activado: análisis automático en segundo plano desactivado."
                    } else {
                        "Análisis automático al instalar o actualizar apps activado."
                    }
                )
            }
        }
    }

    fun setAutoAnalyzeAds(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferencesRepository.setAvAutoAnalyzeAds(enabled)
        }
    }

    fun setScanSystemApps(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferencesRepository.setAvScanSystemApps(enabled)
        }
    }

    fun setIncrementalCache(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferencesRepository.setAvUseIncrementalCache(enabled)
        }
    }

    fun clearScanCache() {
        viewModelScope.launch {
            antivirusRepository.clearScanCache()
            _uiState.update { it.copy(bannerMessage = "Caché de análisis AV eliminada.") }
        }
    }

    fun isolateInWorkProfile(packageName: String) {
        viewModelScope.launch {
            val res = workProfileManager.cloneAppToWorkProfile(packageName)
            val msg = when (res) {
                is ActionResult.Success -> res.message
                is ActionResult.Failure -> res.reason
            }
            _uiState.update { it.copy(bannerMessage = msg) }
        }
    }

    fun freezeApp(packageName: String) {
        viewModelScope.launch {
            val res = when {
                _uiState.value.workProfileStatus.isProfileOwner ->
                    workProfileManager.setAppSuspended(packageName, true)
                _uiState.value.shizukuState.status == ShizukuStatus.ACTIVE ->
                    shizukuManager.setPackageSuspended(packageName, true)
                else -> ActionResult.Failure(
                    "Congelar requiere Perfil de Trabajo (Profile Owner) o Shizuku activo. Abre Ajustes del sistema para desinstalar."
                )
            }
            val msg = when (res) {
                is ActionResult.Success -> res.message
                is ActionResult.Failure -> res.reason
            }
            _uiState.update { it.copy(bannerMessage = msg) }
        }
    }

    fun openSystemSettings(packageName: String) {
        appInspectorRepository.openSystemAppSettings(packageName)
    }

    private fun applyFilters(
        reports: List<AvScanReport>,
        query: String,
        filter: AvFilter
    ): List<AvScanReport> {
        val q = query.trim().lowercase()
        return reports.filter { report ->
            val queryOk = q.isEmpty() ||
                report.appLabel.lowercase().contains(q) ||
                report.packageName.lowercase().contains(q)
            val filterOk = when (filter) {
                AvFilter.ALL -> true
                AvFilter.THREATS ->
                    report.verdict == AvVerdict.MALWARE_DETECTED ||
                        report.verdict == AvVerdict.HIGH_RISK_MALWARE
                AvFilter.RISK -> report.verdict == AvVerdict.RISK
                AvFilter.ADS -> report.adSdks.isNotEmpty() || report.verdict == AvVerdict.ADWARE_INFO
                AvFilter.CLEAN -> report.verdict == AvVerdict.CLEAN
            }
            queryOk && filterOk
        }
    }
}
