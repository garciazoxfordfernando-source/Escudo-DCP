package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import com.example.data.local.db.BlockedAttemptEntity
import com.example.data.local.db.FirewallDao
import com.example.data.local.db.FirewallRuleEntity
import com.example.domain.model.AppEntry
import com.example.domain.model.BlockedAttemptUi
import com.example.domain.model.FirewallRuleUi
import com.example.domain.model.NetworkTransport
import com.example.service.EscudoVpnService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirewallRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firewallDao: FirewallDao
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _vpnActive = MutableStateFlow(false)
    val vpnActive: StateFlow<Boolean> = _vpnActive.asStateFlow()

    private val _activeTransport = MutableStateFlow(detectCurrentTransport())
    val activeTransport: StateFlow<NetworkTransport> = _activeTransport.asStateFlow()

    val rules: Flow<List<FirewallRuleUi>> = firewallDao.observeAllRules()
        .map { list ->
            list.map {
                FirewallRuleUi(
                    packageName = it.packageName,
                    appLabel = it.appLabel,
                    uid = it.uid,
                    isSystemApp = it.isSystemApp,
                    allowWifi = it.allowWifi,
                    allowCellular = it.allowCellular,
                    updatedAt = it.updatedAt
                )
            }
        }
        .flowOn(Dispatchers.IO)

    val blockedAttempts: Flow<List<BlockedAttemptUi>> = firewallDao.observeRecentBlockedAttempts()
        .map { list ->
            list.map {
                BlockedAttemptUi(
                    id = it.id,
                    packageName = it.packageName,
                    appLabel = it.appLabel,
                    uid = it.uid,
                    destinationIp = it.destinationIp,
                    destinationPort = it.destinationPort,
                    protocol = it.protocol,
                    networkType = it.networkType,
                    timestamp = it.timestamp
                )
            }
        }
        .flowOn(Dispatchers.IO)

    fun setVpnActive(active: Boolean) {
        _vpnActive.value = active
        refreshActiveTransport()
    }

    fun refreshActiveTransport() {
        _activeTransport.value = detectCurrentTransport()
    }

    fun detectCurrentTransport(): NetworkTransport {
        val network = connectivityManager.activeNetwork ?: return NetworkTransport.NONE
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkTransport.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.CELLULAR
            else -> NetworkTransport.OTHER
        }
    }

    suspend fun syncAppsIntoRules(apps: List<AppEntry>, defaultAllow: Boolean) = withContext(Dispatchers.IO) {
        val entities = apps.map { app ->
            FirewallRuleEntity(
                packageName = app.packageName,
                appLabel = app.label,
                uid = app.uid,
                isSystemApp = app.isSystemApp,
                allowWifi = defaultAllow,
                allowCellular = defaultAllow
            )
        }
        firewallDao.insertRulesIfAbsent(entities)
    }

    suspend fun toggleWifiRule(packageName: String, allowWifi: Boolean) = withContext(Dispatchers.IO) {
        firewallDao.updateWifiRule(packageName, allowWifi)
        if (_vpnActive.value) notifyServiceReload()
    }

    suspend fun toggleCellularRule(packageName: String, allowCellular: Boolean) = withContext(Dispatchers.IO) {
        firewallDao.updateCellularRule(packageName, allowCellular)
        if (_vpnActive.value) notifyServiceReload()
    }

    suspend fun setAllRules(allowAll: Boolean) = withContext(Dispatchers.IO) {
        firewallDao.setAllRules(allowAll)
        if (_vpnActive.value) notifyServiceReload()
    }

    suspend fun setAppsNetworkAllowed(
        apps: List<AppEntry>,
        allowed: Boolean
    ) = withContext(Dispatchers.IO) {
        if (apps.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val entities = apps.map { app ->
            FirewallRuleEntity(
                packageName = app.packageName,
                appLabel = app.label,
                uid = app.uid,
                isSystemApp = app.isSystemApp,
                allowWifi = allowed,
                allowCellular = allowed,
                updatedAt = now
            )
        }
        firewallDao.upsertRules(entities)
        if (_vpnActive.value) {
            notifyServiceReload()
        }
    }

    suspend fun clearBlockedAttempts() = withContext(Dispatchers.IO) {
        firewallDao.clearBlockedAttempts()
    }

    suspend fun logBlockedAttempt(
        packageName: String,
        appLabel: String,
        uid: Int,
        destinationIp: String,
        destinationPort: Int,
        protocol: String,
        networkType: String
    ) = withContext(Dispatchers.IO) {
        firewallDao.insertBlockedAttempt(
            BlockedAttemptEntity(
                packageName = packageName,
                appLabel = appLabel,
                uid = uid,
                destinationIp = destinationIp,
                destinationPort = destinationPort,
                protocol = protocol,
                networkType = networkType
            )
        )
        firewallDao.pruneOldAttempts()
    }

    suspend fun getRulesSnapshot(): List<FirewallRuleEntity> = withContext(Dispatchers.IO) {
        firewallDao.getAllRulesSnapshot()
    }

    fun prepareVpnIntent(): Intent? = VpnService.prepare(context)

    fun startVpnService() {
        val intent = Intent(context, EscudoVpnService::class.java).apply {
            action = EscudoVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopVpnService() {
        val intent = Intent(context, EscudoVpnService::class.java).apply {
            action = EscudoVpnService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun notifyServiceReload() {
        val intent = Intent(context, EscudoVpnService::class.java).apply {
            action = EscudoVpnService.ACTION_RELOAD
        }
        context.startService(intent)
    }
}
