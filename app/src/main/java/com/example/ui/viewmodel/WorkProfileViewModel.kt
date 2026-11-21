package com.example.ui.viewmodel

import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.system.ActionResult
import com.example.data.system.AppInspectorRepository
import com.example.data.system.WorkProfileManager
import com.example.domain.model.AppEntry
import com.example.domain.model.ProfileScope
import com.example.domain.model.WorkProfileStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class WorkProfileUiState(
    val status: WorkProfileStatus = WorkProfileStatus(
        isDeviceSupported = true,
        isProvisioningAllowed = false,
        isProfileOwner = false,
        hasWorkProfile = false,
        workUserCount = 0,
        restrictionReason = null
    ),
    val workApps: List<AppEntry> = emptyList(),
    val primaryAppsToClone: List<AppEntry> = emptyList(),
    val isBusy: Boolean = false,
    val feedbackMessage: String? = null
)

@HiltViewModel
class WorkProfileViewModel @Inject constructor(
    private val workProfileManager: WorkProfileManager,
    private val appInspectorRepository: AppInspectorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkProfileUiState())
    val uiState: StateFlow<WorkProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val status = workProfileManager.getStatus()
            val workApps = if (status.hasWorkProfile || status.isProfileOwner) {
                appInspectorRepository.loadApps(ProfileScope.WORK, status.isProfileOwner)
            } else {
                emptyList()
            }
            val primaryApps = appInspectorRepository.loadApps(ProfileScope.PRIMARY, false)
                .filter { !it.isInstalledInWorkProfile }
                .take(80)

            _uiState.update {
                it.copy(
                    status = status,
                    workApps = workApps,
                    primaryAppsToClone = primaryApps,
                    isBusy = false
                )
            }
        }
    }

    fun buildProvisioningIntent(): Intent = workProfileManager.buildProvisioningIntent()

    fun cloneApp(packageName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = workProfileManager.cloneAppToWorkProfile(packageName)
            postResult(res)
            refresh()
        }
    }

    fun setAllWorkAppsSuspended(suspended: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val targets = _uiState.value.workApps.filter { !it.isSystemApp }
            var successCount = 0
            targets.forEach { app ->
                val res = workProfileManager.setAppSuspended(app.packageName, suspended)
                if (res is ActionResult.Success) successCount++
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    feedbackMessage = if (suspended) {
                        "Se congelaron $successCount apps de usuario en el perfil de trabajo."
                    } else {
                        "Se descongelaron $successCount apps en el perfil de trabajo."
                    }
                )
            }
            refresh()
        }
    }

    fun wipeWorkProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            val res = workProfileManager.wipeWorkProfile()
            postResult(res)
            refresh()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun postResult(result: ActionResult) {
        val msg = when (result) {
            is ActionResult.Success -> result.message
            is ActionResult.Failure -> result.reason
        }
        _uiState.update { it.copy(isBusy = false, feedbackMessage = msg) }
    }
}
