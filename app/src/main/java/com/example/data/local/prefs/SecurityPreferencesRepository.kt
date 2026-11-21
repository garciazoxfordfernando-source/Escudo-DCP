package com.example.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.local.crypto.KeystoreCryptoManager
import com.example.domain.model.AvScanDepth
import com.example.domain.model.SecurityConfigUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class EncryptedSecurityPayload(
    val biometricLockEnabled: Boolean = false,
    val shizukuModeEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val firewallAutoBlockNewApps: Boolean = false,
    val avScanDepthName: String = AvScanDepth.MAXIMUM.name,
    val avAutoScanEnabled: Boolean = true,
    val avBatterySaverMode: Boolean = false,
    val avAutoAnalyzeAds: Boolean = true,
    val avScanSystemApps: Boolean = false,
    val avUseIncrementalCache: Boolean = true
)

@Singleton
class SecurityPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cryptoManager: KeystoreCryptoManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    val securityConfig: Flow<SecurityConfigUi> = dataStore.data
        .map { prefs ->
            val payload = readPayload(prefs[ENCRYPTED_CONFIG_KEY])
            val depth = runCatching { AvScanDepth.valueOf(payload.avScanDepthName) }
                .getOrDefault(AvScanDepth.MAXIMUM)

            SecurityConfigUi(
                biometricLockEnabled = payload.biometricLockEnabled,
                shizukuModeEnabled = payload.shizukuModeEnabled,
                onboardingCompleted = payload.onboardingCompleted,
                firewallAutoBlockNewApps = payload.firewallAutoBlockNewApps,
                encryptedStorageReady = cryptoManager.isKeystoreReady(),
                avScanDepth = depth,
                avAutoScanEnabled = payload.avAutoScanEnabled,
                avBatterySaverMode = payload.avBatterySaverMode,
                avAutoAnalyzeAds = payload.avAutoAnalyzeAds,
                avScanSystemApps = payload.avScanSystemApps,
                avUseIncrementalCache = payload.avUseIncrementalCache
            )
        }
        .flowOn(Dispatchers.IO)

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        updatePayload { it.copy(biometricLockEnabled = enabled) }
    }

    suspend fun setShizukuModeEnabled(enabled: Boolean) {
        updatePayload { it.copy(shizukuModeEnabled = enabled) }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        updatePayload { it.copy(onboardingCompleted = completed) }
    }

    suspend fun setFirewallAutoBlockNewApps(enabled: Boolean) {
        updatePayload { it.copy(firewallAutoBlockNewApps = enabled) }
    }

    suspend fun setAvScanDepth(depth: AvScanDepth) {
        updatePayload { it.copy(avScanDepthName = depth.name) }
    }

    suspend fun setAvAutoScanEnabled(enabled: Boolean) {
        updatePayload {
            it.copy(
                avAutoScanEnabled = enabled,
                avBatterySaverMode = !enabled
            )
        }
    }

    suspend fun setAvBatterySaverMode(batterySaver: Boolean) {
        updatePayload {
            it.copy(
                avBatterySaverMode = batterySaver,
                avAutoScanEnabled = !batterySaver
            )
        }
    }

    suspend fun setAvAutoAnalyzeAds(enabled: Boolean) {
        updatePayload { it.copy(avAutoAnalyzeAds = enabled) }
    }

    suspend fun setAvScanSystemApps(enabled: Boolean) {
        updatePayload { it.copy(avScanSystemApps = enabled) }
    }

    suspend fun setAvUseIncrementalCache(enabled: Boolean) {
        updatePayload { it.copy(avUseIncrementalCache = enabled) }
    }

    private fun readPayload(encrypted: String?): EncryptedSecurityPayload {
        if (encrypted == null) return EncryptedSecurityPayload()
        return cryptoManager.decrypt(encrypted)?.let { decrypted ->
            runCatching { json.decodeFromString<EncryptedSecurityPayload>(decrypted) }.getOrNull()
        } ?: EncryptedSecurityPayload()
    }

    private suspend fun updatePayload(transform: (EncryptedSecurityPayload) -> EncryptedSecurityPayload) {
        dataStore.edit { prefs ->
            val current = readPayload(prefs[ENCRYPTED_CONFIG_KEY])
            val updated = transform(current)
            val serialized = json.encodeToString(updated)
            prefs[ENCRYPTED_CONFIG_KEY] = cryptoManager.encrypt(serialized)
        }
    }

    companion object {
        private val ENCRYPTED_CONFIG_KEY = stringPreferencesKey("encrypted_security_payload_v1")
    }
}
