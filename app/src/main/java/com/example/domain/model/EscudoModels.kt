package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class ProfileScope {
    PRIMARY,
    WORK
}

enum class RiskLevel(val label: String) {
    HIGH("Alto"),
    MEDIUM("Medio"),
    LOW("Bajo")
}

enum class TrackerCategory(val label: String) {
    ANALYTICS("Analítica"),
    ADS("Publicidad"),
    TELEMETRY("Telemetría y fallos"),
    ATTRIBUTION("Atribución"),
    SOCIAL("Red social / Perfilado")
}

@Immutable
data class PermissionDetail(
    val name: String,
    val shortName: String,
    val description: String,
    val isDangerous: Boolean,
    val isGranted: Boolean,
    val canToggleInCurrentScope: Boolean
)

@Immutable
data class TrackerMatch(
    val id: String,
    val name: String,
    val company: String,
    val category: TrackerCategory,
    val matchedSignature: String
)

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val uid: Int,
    val versionName: String,
    val isSystemApp: Boolean,
    val profileScope: ProfileScope,
    val isInstalledInWorkProfile: Boolean,
    val isSuspended: Boolean,
    val isHidden: Boolean,
    val isEnabled: Boolean,
    val dangerousPermissions: List<PermissionDetail>,
    val grantedDangerousCount: Int,
    val riskLevel: RiskLevel,
    val sourceApkPath: String?,
    val trackers: List<TrackerMatch> = emptyList(),
    val trackersScanned: Boolean = false
)

@Immutable
data class WorkProfileStatus(
    val isDeviceSupported: Boolean,
    val isProvisioningAllowed: Boolean,
    val isProfileOwner: Boolean,
    val hasWorkProfile: Boolean,
    val workUserCount: Int,
    val restrictionReason: String?
)

enum class ShizukuStatus(val label: String) {
    NOT_INSTALLED("Shizuku no instalado"),
    NOT_RUNNING("Instalado sin iniciar"),
    NO_PERMISSION("Sin permiso"),
    ACTIVE("Activo")
}

@Immutable
data class ShizukuState(
    val status: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val serviceBound: Boolean = false,
    val privilegedUid: Int? = null,
    val lastError: String? = null
)

@Immutable
data class FirewallRuleUi(
    val packageName: String,
    val appLabel: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val allowWifi: Boolean,
    val allowCellular: Boolean,
    val updatedAt: Long
)

@Immutable
data class BlockedAttemptUi(
    val id: Long,
    val packageName: String,
    val appLabel: String,
    val uid: Int,
    val destinationIp: String,
    val destinationPort: Int,
    val protocol: String,
    val networkType: String,
    val timestamp: Long
)

enum class NetworkTransport(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("Datos móviles"),
    OTHER("Otra red"),
    NONE("Sin conexión")
}

// --- Open-Source Antivirus & Adware Engine Models ---

enum class AvScanDepth(val label: String, val subtitle: String, val rank: Int) {
    FAST("Rápido", "Firmas de comunidad + permisos críticos (mínimo consumo)", 1),
    STANDARD("Estándar", "Firmas + heurística DEX + auditoría de Ads", 2),
    MAXIMUM("Detección Máxima", "Incluye payloads ocultos en assets/, receptores y origen APK", 3),
    ULTRA("Detección Ultra", "Análisis profundo completo: DEX, ELF/UPX nativo y matriz troyanos", 4)
}

enum class AvVerdict(val label: String, val severityRank: Int) {
    NOT_SCANNED("Sin analizar", 0),
    CLEAN("Limpia", 1),
    ADWARE_INFO("Contiene Ads", 2),
    RISK("Riesgo", 3),
    HIGH_RISK_MALWARE("Alto riesgo malicioso", 4),
    MALWARE_DETECTED("Virus / Amenaza", 5)
}

enum class AvFindingCategory(val label: String) {
    MALWARE_SIGNATURE("Firma Malware"),
    ADWARE("Adware / Ads intrusivos"),
    DYNAMIC_CODE("Carga dinámica / Evasión"),
    SPYWARE("Espionaje / Stalkerware"),
    BANKING_TROJAN("Troyano / Overlay / SMS"),
    EXPLOIT_ROOT("Root / Shell / Packer"),
    SUSPICIOUS_MANIFEST("Manifiesto / Certificado")
}

@Immutable
data class AvFinding(
    val engineName: String,
    val ruleId: String,
    val title: String,
    val description: String,
    val weight: Int,
    val category: AvFindingCategory
)

@Immutable
data class AvScanReport(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val inWorkProfile: Boolean,
    val sha256: String,
    val installerPackage: String?,
    val signerSummary: String,
    val verdict: AvVerdict,
    val threatScore: Int,
    val scanDepth: AvScanDepth,
    val findings: List<AvFinding>,
    val adSdks: List<TrackerMatch>,
    val scanDurationMs: Long,
    val fromCache: Boolean,
    val scannedAt: Long
)

@Immutable
data class SecurityConfigUi(
    val biometricLockEnabled: Boolean = false,
    val shizukuModeEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val firewallAutoBlockNewApps: Boolean = false,
    val encryptedStorageReady: Boolean = true,
    val avScanDepth: AvScanDepth = AvScanDepth.MAXIMUM,
    val avAutoScanEnabled: Boolean = true,
    val avBatterySaverMode: Boolean = false,
    val avAutoAnalyzeAds: Boolean = true,
    val avScanSystemApps: Boolean = false,
    val avUseIncrementalCache: Boolean = true
)

@Immutable
data class CapabilityRow(
    val feature: String,
    val primaryOnly: String,
    val workProfile: String,
    val shizukuPrimary: String
)
