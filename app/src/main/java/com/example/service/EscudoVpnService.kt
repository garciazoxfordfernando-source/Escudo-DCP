package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.db.FirewallRuleEntity
import com.example.data.repository.FirewallRepository
import com.example.domain.model.NetworkTransport
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class EscudoVpnService : VpnService() {

    @Inject
    lateinit var firewallRepository: FirewallRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null
    private var readerJob: Job? = null
    private var networkCallbackRegistered = false

    private val uidToRuleMap = ConcurrentHashMap<Int, FirewallRuleEntity>()
    private val logThrottleMap = ConcurrentHashMap<String, Long>()

    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            firewallRepository.refreshActiveTransport()
            serviceScope.launch { rebuildTunnel() }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            firewallRepository.refreshActiveTransport()
            serviceScope.launch { rebuildTunnel() }
        }

        override fun onLost(network: Network) {
            firewallRepository.refreshActiveTransport()
            serviceScope.launch { rebuildTunnel() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFirewall()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                serviceScope.launch { rebuildTunnel() }
            }
            else -> {
                promoteToForeground()
                firewallRepository.setVpnActive(true)
                serviceScope.launch { rebuildTunnel() }
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopFirewall()
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterNetworkWatcher()
        closeTunnel()
        firewallRepository.setVpnActive(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopFirewall() {
        closeTunnel()
        firewallRepository.setVpnActive(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun promoteToForeground() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EscudoVpnService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(0, "Detener", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun rebuildTunnel() {
        val transport = firewallRepository.detectCurrentTransport()
        val allRules = firewallRepository.getRulesSnapshot()
        val blockedRules = allRules.filter { rule ->
            when (transport) {
                NetworkTransport.WIFI -> !rule.allowWifi
                NetworkTransport.CELLULAR -> !rule.allowCellular
                NetworkTransport.OTHER -> !rule.allowWifi || !rule.allowCellular
                NetworkTransport.NONE -> !rule.allowWifi && !rule.allowCellular
            }
        }

        uidToRuleMap.clear()
        blockedRules.forEach { uidToRuleMap[it.uid] = it }

        val builder = Builder()
            .setSession("Escudo Firewall")
            .setMtu(1500)
            .addAddress("10.111.222.1", 24)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:1111:2222::1", 64)
            .addRoute("::", 0)

        if (blockedRules.isEmpty()) {
            // Ninguna app bloqueada aún: enrutar solo esta propia app (inactiva) para mantener el escudo listo sin afectar al tráfico.
            runCatching { builder.addAllowedApplication(packageName) }
        } else {
            blockedRules.forEach { rule ->
                if (rule.packageName != packageName) {
                    runCatching { builder.addAllowedApplication(rule.packageName) }
                }
            }
        }

        closeTunnel()
        val established = runCatching { builder.establish() }.getOrNull() ?: return
        tunInterface = established
        startPacketDropLoop(established, transport)
    }

    private fun startPacketDropLoop(
        pfd: ParcelFileDescriptor,
        transport: NetworkTransport
    ) {
        readerJob = serviceScope.launch {
            val input = FileInputStream(pfd.fileDescriptor)
            val buffer = ByteBuffer.allocate(32767)
            val array = buffer.array()
            while (isActive) {
                val length = runCatching { input.read(array) }.getOrElse { -1 }
                if (length < 0) break
                if (length == 0) continue

                inspectAndLogBlockedPacket(array, length, transport)
                // Al no reenviar el paquete hacia la red real, el tráfico queda descartado localmente.
            }
        }
    }

    private suspend fun inspectAndLogBlockedPacket(
        packet: ByteArray,
        length: Int,
        transport: NetworkTransport
    ) {
        if (length < 20) return
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4 && version != 6) return

        val protocolNum: Int
        val headerLength: Int
        val srcIpBytes: ByteArray
        val dstIpBytes: ByteArray

        if (version == 4) {
            headerLength = (packet[0].toInt() and 0x0F) * 4
            if (length < headerLength) return
            protocolNum = packet[9].toInt() and 0xFF
            srcIpBytes = packet.copyOfRange(12, 16)
            dstIpBytes = packet.copyOfRange(16, 20)
        } else {
            if (length < 40) return
            headerLength = 40
            protocolNum = packet[6].toInt() and 0xFF
            srcIpBytes = packet.copyOfRange(8, 24)
            dstIpBytes = packet.copyOfRange(24, 40)
        }

        val protoName = when (protocolNum) {
            OsConstants.IPPROTO_TCP -> "TCP"
            OsConstants.IPPROTO_UDP -> "UDP"
            OsConstants.IPPROTO_ICMP, OsConstants.IPPROTO_ICMPV6 -> "ICMP"
            else -> "IP($protocolNum)"
        }

        var srcPort = 0
        var dstPort = 0
        if ((protocolNum == OsConstants.IPPROTO_TCP || protocolNum == OsConstants.IPPROTO_UDP) &&
            length >= headerLength + 4
        ) {
            srcPort = ((packet[headerLength].toInt() and 0xFF) shl 8) or
                (packet[headerLength + 1].toInt() and 0xFF)
            dstPort = ((packet[headerLength + 2].toInt() and 0xFF) shl 8) or
                (packet[headerLength + 3].toInt() and 0xFF)
        }

        val srcAddr = runCatching { InetAddress.getByAddress(srcIpBytes) }.getOrNull() ?: return
        val dstAddr = runCatching { InetAddress.getByAddress(dstIpBytes) }.getOrNull() ?: return
        val dstIpText = dstAddr.hostAddress ?: return

        var resolvedUid = -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (protocolNum == OsConstants.IPPROTO_TCP || protocolNum == OsConstants.IPPROTO_UDP)
        ) {
            resolvedUid = runCatching {
                connectivityManager.getConnectionOwnerUid(
                    protocolNum,
                    InetSocketAddress(srcAddr, srcPort),
                    InetSocketAddress(dstAddr, dstPort)
                )
            }.getOrDefault(-1)
        }

        val matchedRule = when {
            resolvedUid > 0 -> uidToRuleMap[resolvedUid]
            uidToRuleMap.size == 1 -> uidToRuleMap.values.firstOrNull()
            else -> null
        }

        val pkgName = matchedRule?.packageName ?: "uid.$resolvedUid"
        val appLabel = matchedRule?.appLabel ?: if (resolvedUid > 0) "UID $resolvedUid" else "App bloqueada"
        val logKey = "$pkgName:$dstIpText:$dstPort:$protoName"
        val now = System.currentTimeMillis()
        val lastLogged = logThrottleMap[logKey] ?: 0L
        if (now - lastLogged < 4_000L) return
        logThrottleMap[logKey] = now

        firewallRepository.logBlockedAttempt(
            packageName = pkgName,
            appLabel = appLabel,
            uid = if (resolvedUid > 0) resolvedUid else (matchedRule?.uid ?: -1),
            destinationIp = dstIpText,
            destinationPort = dstPort,
            protocol = protoName,
            networkType = transport.label
        )
    }

    private fun closeTunnel() {
        readerJob?.cancel()
        readerJob = null
        runCatching { tunInterface?.close() }
        tunInterface = null
    }

    private fun registerNetworkWatcher() {
        if (networkCallbackRegistered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        }
    }

    private fun unregisterNetworkWatcher() {
        if (!networkCallbackRegistered) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.vpn_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.escudo.vpn.START"
        const val ACTION_STOP = "com.example.escudo.vpn.STOP"
        const val ACTION_RELOAD = "com.example.escudo.vpn.RELOAD"
        private const val CHANNEL_ID = "escudo_vpn_firewall_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
