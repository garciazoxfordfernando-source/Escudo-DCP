package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.prefs.SecurityPreferencesRepository
import com.example.domain.model.SecurityConfigUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityGateViewModel @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) : ViewModel() {

    val securityConfig: StateFlow<SecurityConfigUi> = securityPreferencesRepository.securityConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SecurityConfigUi()
        )

    private val _isUnlocked = MutableStateFlow(true)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _gateEvaluated = MutableStateFlow(false)
    val gateEvaluated: StateFlow<Boolean> = _gateEvaluated.asStateFlow()

    init {
        viewModelScope.launch {
            val initial = securityPreferencesRepository.securityConfig.first()
            _isUnlocked.value = !initial.biometricLockEnabled
            _gateEvaluated.value = true
        }
    }

    fun onBiometricSuccess() {
        _isUnlocked.value = true
    }

    fun lockNowIfEnabled() {
        if (securityConfig.value.biometricLockEnabled) {
            _isUnlocked.value = false
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            securityPreferencesRepository.setOnboardingCompleted(true)
        }
    }
}
