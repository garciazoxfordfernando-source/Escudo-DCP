package com.example.ui.viewmodel

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.tracker.TrackerSignatureDatabase
import com.example.data.repository.FirewallRepository
import com.example.data.system.AppInspectorRepository
import com.example.domain.model.AppEntry
import com.example.domain.model.ProfileScope
import com.example.domain.model.TrackerCategory
import com.example.domain.model.TrackerMatch
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

sealed interface TrackerBlockRequest {
    data class SingleApp(val app: AppEntry) : TrackerBlockRequest
    data class BatchApps(
        val userApps: List<AppEntry>,
        val systemApps: List<AppEntry>
    ) : TrackerBlockRequest
}

@Immutable
data class TrackersUiState(
    val isLoadingApps: Boolean = true,
    val isScanningBatch: Boolean = false,
    val scannedCount: Int = 0,
    val totalToScan: Int = 0,
    val onlyWithTrackers: Boolean = false,
    val selectedCategory: TrackerCategory? = null,
    val knownSignaturesCount: Int = TrackerSignatureDatabase.signatures.size,
    val apps: List<AppEntry> = emptyList(),
    val filteredApps: List<AppEntry> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
    val vpnActive: Boolean = false,
    val expandedPackage: String? = null,
    val pendingBlockRequest: TrackerBlockRequest? = null,
    val bannerMessage: String? = null
)

@HiltViewModel
class TrackersViewModel @Inject constructor(
    private val appInspectorRepository: AppInspectorRepository,
    private val firewallRepository: FirewallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackersUiState())
    val uiState: StateFlow<TrackersUiState> = _uiState.asStateFlow()

    private var batchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                firewallRepository.rules,
                firewallRepository.vpnActive
            ) { rules, vpnActive ->
                val blocked = rules
                    .asSequence()
                    .filter { !it.allowWifi && !it.allowCellular }
                    .map { it.packageName }
                    .toSet()
                blocked to vpnActive
            }.collect { (blocked, vpnActive) ->
                _uiState.update {
                    it.copy(
                        blockedPackages = blocked,
                        vpnActive = vpnActive
                    )
                }
            }
        }
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val loaded = appInspectorRepository.loadApps(ProfileScope.PRIMARY, canModifyCurrentScope = false)
                .sortedWith(compareByDescending<AppEntry> { it.trackers.size }.thenBy { it.label.lowercase() })
            _uiState.update { state ->
                state.copy(
                    isLoadingApps = false,
                    apps = loaded,
                    filteredApps = applyFilter(loaded, state.onlyWithTrackers, state.selectedCategory)
                )
            }
        }
    }

    fun scanAllApps() {
        if (batchJob?.isActive == true) return
        batchJob = viewModelScope.launch {
            val snapshot = _uiState.value.apps
            val total = snapshot.size
            _uiState.update { it.copy(isScanningBatch = true, scannedCount = 0, totalToScan = total) }

            val updatedList = snapshot.toMutableList()
            for (index in snapshot.indices) {
                val scanned = appInspectorRepository.scanTrackersForApp(snapshot[index])
                updatedList[index] = scanned
                // Actualizar la UI por lotes cada 4 apps (o al final) para evitar recomposiciones excesivas
                if ((index + 1) % 4 == 0 || index == snapshot.lastIndex) {
                    val sorted = updatedList.sortedWith(
                        compareByDescending<AppEntry> { it.trackers.size }.thenBy { it.label.lowercase() }
                    )
                    _uiState.update { state ->
                        state.copy(
                            scannedCount = index + 1,
                            apps = sorted,
                            filteredApps = applyFilter(sorted, state.onlyWithTrackers, state.selectedCategory)
                        )
                    }
                } else {
                    _uiState.update { it.copy(scannedCount = index + 1) }
                }
                yield()
            }

            _uiState.update { it.copy(isScanningBatch = false) }
        }
    }

    fun scanSingleApp(app: AppEntry) {
        viewModelScope.launch {
            val scanned = appInspectorRepository.scanTrackersForApp(app)
            _uiState.update { state ->
                val updated = state.apps.map { if (it.packageName == scanned.packageName) scanned else it }
                    .sortedWith(compareByDescending<AppEntry> { it.trackers.size }.thenBy { it.label.lowercase() })
                state.copy(
                    apps = updated,
                    filteredApps = applyFilter(updated, state.onlyWithTrackers, state.selectedCategory),
                    expandedPackage = scanned.packageName
                )
            }
        }
    }

    fun requestBlockTrackers(app: AppEntry) {
        _uiState.update {
            it.copy(pendingBlockRequest = TrackerBlockRequest.SingleApp(app))
        }
    }

    fun requestBlockAllDetectedTrackers() {
        val withTrackers = _uiState.value.apps.filter { it.trackers.isNotEmpty() }
        if (withTrackers.isEmpty()) {
            _uiState.update {
                it.copy(bannerMessage = "Escanea las aplicaciones primero para identificar cuáles contienen rastreadores.")
            }
            return
        }
        val userApps = withTrackers.filter { !it.isSystemApp }
        val systemApps = withTrackers.filter { it.isSystemApp }
        _uiState.update {
            it.copy(
                pendingBlockRequest = TrackerBlockRequest.BatchApps(
                    userApps = userApps,
                    systemApps = systemApps
                )
            )
        }
    }

    fun dismissBlockConfirmation() {
        _uiState.update { it.copy(pendingBlockRequest = null) }
    }

    fun confirmBlock(includeSystemApps: Boolean, onNeedVpnConsent: (Intent) -> Unit) {
        val request = _uiState.value.pendingBlockRequest ?: return
        _uiState.update { it.copy(pendingBlockRequest = null) }

        val targets = when (request) {
            is TrackerBlockRequest.SingleApp -> listOf(request.app)
            is TrackerBlockRequest.BatchApps -> {
                if (includeSystemApps) request.userApps + request.systemApps else request.userApps
            }
        }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(bannerMessage = "No hay aplicaciones de usuario con rastreadores en esta selección.") }
            return
        }

        viewModelScope.launch {
            firewallRepository.setAppsNetworkAllowed(targets, allowed = false)
            if (!firewallRepository.vpnActive.value) {
                val prepare = firewallRepository.prepareVpnIntent()
                if (prepare != null) {
                    onNeedVpnConsent(prepare)
                } else {
                    firewallRepository.startVpnService()
                }
            }
            _uiState.update {
                it.copy(
                    bannerMessage = "Rastreadores bloqueados en Firewall para ${targets.size} app(s) (Wi-Fi y Datos)."
                )
            }
        }
    }

    fun unblockAppTrackers(app: AppEntry) {
        viewModelScope.launch {
            firewallRepository.setAppsNetworkAllowed(listOf(app), allowed = true)
            _uiState.update {
                it.copy(bannerMessage = "Conectividad restaurada para ${app.label}.")
            }
        }
    }

    fun startVpnAfterConsent() {
        firewallRepository.startVpnService()
    }

    fun openSystemAppSettings(packageName: String) {
        appInspectorRepository.openSystemAppSettings(packageName)
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

    fun clearMessage() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun toggleOnlyWithTrackers() {
        _uiState.update { state ->
            val next = !state.onlyWithTrackers
            state.copy(
                onlyWithTrackers = next,
                filteredApps = applyFilter(state.apps, next, state.selectedCategory)
            )
        }
    }

    fun selectCategory(category: TrackerCategory?) {
        _uiState.update { state ->
            val next = if (state.selectedCategory == category) null else category
            state.copy(
                selectedCategory = next,
                filteredApps = applyFilter(state.apps, state.onlyWithTrackers, next)
            )
        }
    }

    fun toggleExpanded(packageName: String) {
        _uiState.update { state ->
            state.copy(expandedPackage = if (state.expandedPackage == packageName) null else packageName)
        }
    }

    private fun applyFilter(
        apps: List<AppEntry>,
        onlyTrackers: Boolean,
        category: TrackerCategory?
    ): List<AppEntry> {
        return apps.filter { app ->
            val countOk = !onlyTrackers || app.trackers.isNotEmpty()
            val catOk = category == null || app.trackers.any { it.category == category }
            countOk && catOk
        }
    }
}
