package com.example.ui.viewmodel

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.prefs.SecurityPreferencesRepository
import com.example.data.repository.FirewallRepository
import com.example.data.system.AppInspectorRepository
import com.example.domain.model.BlockedAttemptUi
import com.example.domain.model.FirewallRuleUi
import com.example.domain.model.NetworkTransport
import com.example.domain.model.ProfileScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FirewallTab(val label: String) {
    RULES("Reglas por App"),
    LOGS("Intentos bloqueados")
}

@Immutable
data class FirewallUiState(
    val vpnActive: Boolean = false,
    val activeTransport: NetworkTransport = NetworkTransport.NONE,
    val selectedTab: FirewallTab = FirewallTab.RULES,
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val allRules: List<FirewallRuleUi> = emptyList(),
    val filteredRules: List<FirewallRuleUi> = emptyList(),
    val blockedAttempts: List<BlockedAttemptUi> = emptyList(),
    val statusMessage: String? = null
)

@HiltViewModel
class FirewallViewModel @Inject constructor(
    private val firewallRepository: FirewallRepository,
    private val appInspectorRepository: AppInspectorRepository,
    private val securityPreferencesRepository: SecurityPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirewallUiState())
    val uiState: StateFlow<FirewallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                firewallRepository.vpnActive,
                firewallRepository.activeTransport,
                firewallRepository.rules,
                firewallRepository.blockedAttempts
            ) { active, transport, rules, logs ->
                Quad(active, transport, rules, logs)
            }.collect { (active, transport, rules, logs) ->
                _uiState.update { state ->
                    state.copy(
                        vpnActive = active,
                        activeTransport = transport,
                        allRules = rules,
                        filteredRules = filterRules(rules, state.searchQuery, state.showSystemApps),
                        blockedAttempts = logs
                    )
                }
            }
        }
        syncInventory()
    }

    fun syncInventory() {
        viewModelScope.launch {
            val apps = appInspectorRepository.loadApps(ProfileScope.PRIMARY, canModifyCurrentScope = false)
            val defaultAllow = !securityPreferencesRepository.securityConfig.first().firewallAutoBlockNewApps
            firewallRepository.syncAppsIntoRules(apps, defaultAllow)
        }
    }

    fun selectTab(tab: FirewallTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearch(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredRules = filterRules(state.allRules, query, state.showSystemApps)
            )
        }
    }

    fun toggleShowSystemApps() {
        _uiState.update { state ->
            val next = !state.showSystemApps
            state.copy(
                showSystemApps = next,
                filteredRules = filterRules(state.allRules, state.searchQuery, next)
            )
        }
    }

    fun toggleWifiRule(packageName: String, allowWifi: Boolean) {
        viewModelScope.launch {
            firewallRepository.toggleWifiRule(packageName, allowWifi)
        }
    }

    fun toggleCellularRule(packageName: String, allowCellular: Boolean) {
        viewModelScope.launch {
            firewallRepository.toggleCellularRule(packageName, allowCellular)
        }
    }

    fun setAllRules(allowAll: Boolean) {
        viewModelScope.launch {
            firewallRepository.setAllRules(allowAll)
            _uiState.update {
                it.copy(
                    statusMessage = if (allowAll) {
                        "Todas las apps permitidas en Wi-Fi y datos."
                    } else {
                        "Bloqueo total aplicado en Wi-Fi y datos."
                    }
                )
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            firewallRepository.clearBlockedAttempts()
            _uiState.update { it.copy(statusMessage = "Registro de intentos bloqueados limpio.") }
        }
    }

    fun prepareVpnIntent(): Intent? = firewallRepository.prepareVpnIntent()

    fun startVpn() {
        firewallRepository.startVpnService()
    }

    fun stopVpn() {
        firewallRepository.stopVpnService()
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun filterRules(
        rules: List<FirewallRuleUi>,
        query: String,
        showSystem: Boolean
    ): List<FirewallRuleUi> {
        val q = query.trim().lowercase()
        return rules.filter { rule ->
            val systemOk = showSystem || !rule.isSystemApp
            val queryOk = q.isEmpty() ||
                rule.appLabel.lowercase().contains(q) ||
                rule.packageName.lowercase().contains(q)
            systemOk && queryOk
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
