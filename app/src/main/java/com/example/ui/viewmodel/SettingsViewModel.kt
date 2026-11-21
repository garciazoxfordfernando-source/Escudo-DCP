package com.example.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.prefs.SecurityPreferencesRepository
import com.example.data.system.ShizukuManager
import com.example.data.system.WorkProfileManager
import com.example.domain.model.CapabilityRow
import com.example.domain.model.SecurityConfigUi
import com.example.domain.model.ShizukuState
import com.example.domain.model.WorkProfileStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val securityConfig: SecurityConfigUi = SecurityConfigUi(),
    val shizukuState: ShizukuState = ShizukuState(),
    val workProfileStatus: WorkProfileStatus = WorkProfileStatus(
        isDeviceSupported = true,
        isProvisioningAllowed = false,
        isProfileOwner = false,
        hasWorkProfile = false,
        workUserCount = 0,
        restrictionReason = null
    ),
    val capabilityMatrix: List<CapabilityRow> = defaultCapabilityMatrix,
    val feedbackMessage: String? = null
)

private val defaultCapabilityMatrix = listOf(
    CapabilityRow(
        feature = "Leer apps y permisos",
        primaryOnly = "Sí (solo lectura)",
        workProfile = "Sí",
        shizukuPrimary = "Sí"
    ),
    CapabilityRow(
        feature = "Clonar y aislar datos",
        primaryOnly = "No",
        workProfile = "Sí (Profile Owner)",
        shizukuPrimary = "No (usa Perfil Trabajo)"
    ),
    CapabilityRow(
        feature = "Congelar / Suspender",
        primaryOnly = "No",
        workProfile = "Sí (setPackagesSuspended)",
        shizukuPrimary = "Sí (pm suspend / disable-user)"
    ),
    CapabilityRow(
        feature = "Ocultar apps",
        primaryOnly = "No",
        workProfile = "Sí (setApplicationHidden)",
        shizukuPrimary = "Parcial (pm hide según ROM)"
    ),
    CapabilityRow(
        feature = "Revocar/Conceder permisos",
        primaryOnly = "Solo abre Ajustes",
        workProfile = "Sí (setPermissionGrantState)",
        shizukuPrimary = "Sí (pm revoke / grant)"
    ),
    CapabilityRow(
        feature = "Firewall Wi-Fi / Datos",
        primaryOnly = "Sí (VpnService local)",
        workProfile = "Sí (VpnService local)",
        shizukuPrimary = "Sí (VpnService local)"
    ),
    CapabilityRow(
        feature = "Escáner SDK rastreadores",
        primaryOnly = "Sí (lectura APK local)",
        workProfile = "Sí (lectura APK local)",
        shizukuPrimary = "Sí (lectura APK local)"
    )
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val shizukuManager: ShizukuManager,
    private val workProfileManager: WorkProfileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                securityPreferencesRepository.securityConfig,
                shizukuManager.state
            ) { config, shizuku ->
                config to shizuku
            }.collect { (config, shizuku) ->
                _uiState.update {
                    it.copy(
                        securityConfig = config,
                        shizukuState = shizuku,
                        workProfileStatus = workProfileManager.getStatus()
                    )
                }
            }
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferencesRepository.setBiometricLockEnabled(enabled)
            _uiState.update {
                it.copy(
                    feedbackMessage = if (enabled) {
                        "Bloqueo biométrico activado y cifrado con Android Keystore."
                    } else {
                        "Bloqueo biométrico desactivado."
                    }
                )
            }
        }
    }

    fun setShizukuModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferencesRepository.setShizukuModeEnabled(enabled)
        }
    }

    fun setFirewallAutoBlockNewApps(enabled: Boolean) {
        viewModelScope.launch {
            securityPreferencesRepository.setFirewallAutoBlockNewApps(enabled)
        }
    }

    fun refreshShizuku() {
        shizukuManager.refreshStatus()
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun clearMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
