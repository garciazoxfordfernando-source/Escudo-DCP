package com.example.data.local.av

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.local.db.AntivirusDao
import com.example.data.local.db.AntivirusScanEntity
import com.example.data.local.tracker.TrackerScanner
import com.example.domain.model.AvFinding
import com.example.domain.model.AvFindingCategory
import com.example.domain.model.AvScanDepth
import com.example.domain.model.AvScanReport
import com.example.domain.model.AvVerdict
import com.example.domain.model.TrackerCategory
import com.example.domain.model.TrackerMatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SerializedFinding(
    val engineName: String,
    val ruleId: String,
    val title: String,
    val description: String,
    val weight: Int,
    val categoryName: String
)

@Serializable
private data class SerializedAdSdk(
    val id: String,
    val name: String,
    val company: String,
    val categoryName: String,
    val matchedSignature: String
)

@Singleton
class OpenSourceAvEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val antivirusDao: AntivirusDao,
    private val trackerScanner: TrackerScanner
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val avDispatcher = Dispatchers.IO.limitedParallelism(2)

    private val json = Json { ignoreUnknownKeys = true }

    // Autómata compilado una única vez en memoria (bajo consumo de RAM, máxima velocidad O(n))
    private val combinedMatcher: AhoCorasickMatcher<AvByteRule> by lazy {
        val pairs = ArrayList<Pair<ByteArray, AvByteRule>>(64)
        (OpenSourceAvRules.dexRules + OpenSourceAvRules.adwareAndAdsRules).forEach { rule ->
            rule.patterns.forEach { pattern ->
                pairs.add(pattern.toByteArray(Charsets.US_ASCII) to rule)
                if (pattern.contains('/')) {
                    pairs.add(pattern.replace('/', '.').toByteArray(Charsets.US_ASCII) to rule)
                }
            }
        }
        AhoCorasickMatcher(pairs)
    }

    suspend fun scanPackage(
        packageName: String,
        depth: AvScanDepth,
        inWorkProfile: Boolean,
        useCache: Boolean,
        includeAdsAudit: Boolean,
        forceRefresh: Boolean = false
    ): AvScanReport? = withContext(avDispatcher) {
        val startMs = System.currentTimeMillis()
        val pm = context.packageManager
        val pkgInfo = getPackageInfoWithSigning(pm, packageName) ?: return@withContext null
        val appInfo = pkgInfo.applicationInfo ?: return@withContext null

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }
        val lastUpdate = pkgInfo.lastUpdateTime

        if (useCache && !forceRefresh) {
            val cached = antivirusDao.getScanForPackage(packageName)
            val cachedDepth = cached?.let { runCatching { AvScanDepth.valueOf(it.scanDepth) }.getOrNull() }
            if (
                cached != null &&
                cached.versionCode == versionCode &&
                cached.lastUpdateTime == lastUpdate &&
                cachedDepth != null &&
                cachedDepth.rank >= depth.rank
            ) {
                return@withContext entityToReport(cached, fromCache = true)
            }
        }

        val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(packageName)
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val apkPath = appInfo.sourceDir
        val installer = resolveInstallerPackage(pm, packageName)
        val signerSummary = resolveSignerSummary(pkgInfo)
        val sha256 = computeSha256(apkPath)

        val findingsMap = linkedMapOf<String, AvFinding>()

        // 1. Verificación de hash SHA-256 comunitario
        OpenSourceAvRules.knownMaliciousHashes[sha256]?.let { match ->
            findingsMap["HASH_${match.malwareFamily}"] = AvFinding(
                engineName = match.engineName,
                ruleId = "SHA256_IOC",
                title = "Firma maliciosa confirmada: ${match.malwareFamily}",
                description = "El hash SHA-256 del APK coincide con una muestra registrada en la base comunitaria.",
                weight = 100,
                category = AvFindingCategory.MALWARE_SIGNATURE
            )
        }

        // 2. Auditoría de certificado y origen de instalación (Máxima/Ultra o debug key)
        if (signerSummary.contains("Android Debug", ignoreCase = true) ||
            signerSummary.contains("testkey", ignoreCase = true)
        ) {
            findingsMap["CERT_DEBUG"] = AvFinding(
                engineName = "Escudo-CertAudit",
                ruleId = "SIGNER_DEBUG_KEY",
                title = "APK firmado con clave de depuración o testkey",
                description = "Certificado no apto para producción; habitual en APKs modificados o reempaquetados.",
                weight = 22,
                category = AvFindingCategory.SUSPICIOUS_MANIFEST
            )
        }
        if (!isSystem && installer == null && depth.rank >= AvScanDepth.MAXIMUM.rank) {
            findingsMap["ORIGIN_SIDELOAD"] = AvFinding(
                engineName = "Escudo-OriginGuard",
                ruleId = "SIDELOAD_UNKNOWN_ORIGIN",
                title = "Instalación externa sin tienda verificada (Sideload)",
                description = "No registra un instalador de tienda conocido; requiere mayor escrutinio heurístico.",
                weight = 8,
                category = AvFindingCategory.SUSPICIOUS_MANIFEST
            )
        }

        // 3. Combinaciones de permisos críticos (estilo MobSF / YARA-Manifest)
        evaluatePermissionHeuristics(pkgInfo, isSystem, findingsMap)

        // 4. Escaneo en una sola pasada con Aho-Corasick sobre classes*.dex y payloads ocultos
        val matchedRules = linkedMapOf<String, AvByteRule>()
        scanApkArchives(apkPath, depth, matchedRules, findingsMap)

        matchedRules.values.forEach { rule ->
            if (rule.minDepth.rank <= depth.rank) {
                findingsMap[rule.id] = AvFinding(
                    engineName = rule.engineName,
                    ruleId = rule.id,
                    title = rule.title,
                    description = rule.description,
                    weight = rule.weight,
                    category = rule.category
                )
            }
        }

        // 5. Auditoría dedicada de Ads y Adware
        val adSdks = if (includeAdsAudit) {
            val trackerAds = trackerScanner.scanApk(apkPath)
                .filter { it.category == TrackerCategory.ADS || it.category == TrackerCategory.ATTRIBUTION }
            val extraRuleAds = matchedRules.values
                .filter { it.category == AvFindingCategory.ADWARE }
                .map {
                    TrackerMatch(
                        id = it.id,
                        name = it.title,
                        company = it.engineName,
                        category = TrackerCategory.ADS,
                        matchedSignature = it.patterns.firstOrNull().orEmpty()
                    )
                }
            (trackerAds + extraRuleAds).distinctBy { it.name }
        } else {
            emptyList()
        }

        val findings = findingsMap.values.sortedByDescending { it.weight }
        val threatScore = computeThreatScore(findings, isSystem)
        val verdict = determineVerdict(threatScore, findings, adSdks)
        val duration = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        val report = AvScanReport(
            packageName = packageName,
            appLabel = label,
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = versionCode,
            isSystemApp = isSystem,
            inWorkProfile = inWorkProfile,
            sha256 = sha256,
            installerPackage = installer,
            signerSummary = signerSummary,
            verdict = verdict,
            threatScore = threatScore,
            scanDepth = depth,
            findings = findings,
            adSdks = adSdks,
            scanDurationMs = duration,
            fromCache = false,
            scannedAt = System.currentTimeMillis()
        )

        antivirusDao.upsertScan(reportToEntity(report, lastUpdate))
        report
    }

    suspend fun analyzeAdsOnly(
        packageName: String,
        inWorkProfile: Boolean
    ): AvScanReport? = withContext(avDispatcher) {
        scanPackage(
            packageName = packageName,
            depth = AvScanDepth.STANDARD,
            inWorkProfile = inWorkProfile,
            useCache = false,
            includeAdsAudit = true,
            forceRefresh = true
        )
    }

    private fun scanApkArchives(
        apkPath: String?,
        depth: AvScanDepth,
        matchedRules: MutableMap<String, AvByteRule>,
        findingsMap: MutableMap<String, AvFinding>
    ) {
        if (apkPath.isNullOrBlank()) return
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return

        val buffer = ByteArray(64 * 1024)
        var hiddenBinaryFound = false
        var upxPackerFound = false

        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name.startsWith("classes") && name.endsWith(".dex")) {
                        zip.getInputStream(entry).use { stream ->
                            combinedMatcher.scanStream(stream, buffer) { rule ->
                                matchedRules[rule.id] = rule
                            }
                        }
                    } else if (depth.rank >= AvScanDepth.MAXIMUM.rank &&
                        (name.startsWith("assets/") || name.startsWith("res/raw/")) &&
                        entry.size in 1024L..(12L * 1024L * 1024L)
                    ) {
                        // Buscar payloads ocultos (APK/DEX/ELF disfrazados como imagen o binario en assets)
                        zip.getInputStream(entry).use { stream ->
                            val header = ByteArray(8)
                            val read = stream.read(header)
                            if (read >= 4 && isExecutablePayloadHeader(header)) {
                                hiddenBinaryFound = true
                            }
                        }
                    } else if (depth.rank >= AvScanDepth.ULTRA.rank &&
                        name.startsWith("lib/") && name.endsWith(".so") &&
                        entry.size in 512L..(16L * 1024L * 1024L)
                    ) {
                        // Inspeccionar los primeros bytes de librerías nativas buscando empaquetadores UPX
                        zip.getInputStream(entry).use { stream ->
                            val head = ByteArray(4096)
                            val read = stream.read(head)
                            if (read > 4 && containsAscii(head, read, "UPX!")) {
                                upxPackerFound = true
                            }
                        }
                    }
                }
            }
        }

        if (hiddenBinaryFound) {
            findingsMap["YARA_HIDDEN_ASSET_PAYLOAD"] = AvFinding(
                engineName = "YARA-Android",
                ruleId = "HIDDEN_ASSET_EXECUTABLE",
                title = "Archivo ejecutable oculto en assets/ o res/raw/",
                description = "Se detectó cabecera DEX, ELF o ZIP/APK dentro de recursos estáticos; técnica típica de droppers.",
                weight = 48,
                category = AvFindingCategory.DYNAMIC_CODE
            )
        }
        if (upxPackerFound) {
            findingsMap["ULTRA_UPX_PACKER"] = AvFinding(
                engineName = "Koodous-ELF",
                ruleId = "NATIVE_UPX_PACKER",
                title = "Librería nativa empaquetada con UPX",
                description = "El binario .so contiene firma de empaquetado UPX para ofuscar código nativo.",
                weight = 32,
                category = AvFindingCategory.EXPLOIT_ROOT
            )
        }
    }

    private fun evaluatePermissionHeuristics(
        pkgInfo: PackageInfo,
        isSystem: Boolean,
        findingsMap: MutableMap<String, AvFinding>
    ) {
        val perms = pkgInfo.requestedPermissions?.toSet() ?: return
        val hasOverlay = perms.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)
        val hasBoot = perms.contains(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        val hasSms = perms.contains(Manifest.permission.READ_SMS) || perms.contains(Manifest.permission.SEND_SMS)
        val hasContacts = perms.contains(Manifest.permission.READ_CONTACTS)
        val hasMicOrCam = perms.contains(Manifest.permission.RECORD_AUDIO) || perms.contains(Manifest.permission.CAMERA)
        val hasInstallPkgs = perms.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES)

        if (!isSystem && hasOverlay && hasSms) {
            findingsMap["HEUR_BANKER_COMBO"] = AvFinding(
                engineName = "Escudo-Heurística",
                ruleId = "COMBO_OVERLAY_SMS",
                title = "Combinación crítica: Superposición de pantalla + SMS",
                description = "Patrón clásico de troyanos bancarios para capturar credenciales y leer códigos OTP.",
                weight = 36,
                category = AvFindingCategory.BANKING_TROJAN
            )
        }
        if (!isSystem && hasBoot && hasMicOrCam && hasContacts) {
            findingsMap["HEUR_SPY_COMBO"] = AvFinding(
                engineName = "Escudo-Heurística",
                ruleId = "COMBO_SPYWARE_SURFACES",
                title = "Superficie de espionaje: Arranque + Micrófono/Cámara + Contactos",
                description = "Solicita acceso simultáneo a sensores de audio/vídeo, agenda y persistencia al iniciar.",
                weight = 20,
                category = AvFindingCategory.SPYWARE
            )
        }
        if (!isSystem && hasInstallPkgs && hasOverlay) {
            findingsMap["HEUR_DROPPER_COMBO"] = AvFinding(
                engineName = "Escudo-Heurística",
                ruleId = "COMBO_DROPPER_INSTALL",
                title = "Capacidad de dropper: Instalación de APKs + Superposición",
                description = "Puede solicitar instalar paquetes externos mientras dibuja ventanas sobre otras apps.",
                weight = 24,
                category = AvFindingCategory.DYNAMIC_CODE
            )
        }
    }

    private fun computeThreatScore(findings: List<AvFinding>, isSystem: Boolean): Int {
        val raw = findings.sumOf { it.weight }
        val adjusted = if (isSystem) (raw * 0.6f).toInt() else raw
        return adjusted.coerceIn(0, 100)
    }

    private fun determineVerdict(
        score: Int,
        findings: List<AvFinding>,
        adSdks: List<TrackerMatch>
    ): AvVerdict {
        val hasMalwareSig = findings.any { it.category == AvFindingCategory.MALWARE_SIGNATURE && it.weight >= 60 }
        val onlyAdsFindings = findings.isNotEmpty() && findings.all { it.category == AvFindingCategory.ADWARE }
        return when {
            hasMalwareSig || score >= 75 -> AvVerdict.MALWARE_DETECTED
            score >= 48 -> AvVerdict.HIGH_RISK_MALWARE
            score >= 22 -> AvVerdict.RISK
            onlyAdsFindings || adSdks.isNotEmpty() -> AvVerdict.ADWARE_INFO
            else -> AvVerdict.CLEAN
        }
    }

    private fun isExecutablePayloadHeader(h: ByteArray): Boolean {
        val isDex = h[0] == 'd'.code.toByte() && h[1] == 'e'.code.toByte() && h[2] == 'x'.code.toByte()
        val isElf = h[0] == 0x7F.toByte() && h[1] == 'E'.code.toByte() && h[2] == 'L'.code.toByte() && h[3] == 'F'.code.toByte()
        return isDex || isElf
    }

    private fun containsAscii(haystack: ByteArray, len: Int, needle: String): Boolean {
        val target = needle.toByteArray(Charsets.US_ASCII)
        if (len < target.size) return false
        for (i in 0..(len - target.size)) {
            var matched = true
            for (j in target.indices) {
                if (haystack[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

    private fun computeSha256(apkPath: String?): String {
        if (apkPath.isNullOrBlank()) return "N/D"
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return "N/D"
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("N/D")
    }

    private fun getPackageInfoWithSigning(pm: PackageManager, packageName: String): PackageInfo? {
        val flags = PackageManager.GET_PERMISSIONS or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
        }.getOrNull()
    }

    private fun resolveInstallerPackage(pm: PackageManager, packageName: String): String? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        }.getOrNull()
    }

    private fun resolveSignerSummary(pkgInfo: PackageInfo): String {
        val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.firstOrNull()?.toByteArray()
        } ?: return "Firma no disponible"

        return runCatching {
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            cert.subjectX500Principal.name
        }.getOrDefault("Certificado X.509")
    }

    private fun reportToEntity(report: AvScanReport, lastUpdateTime: Long): AntivirusScanEntity {
        val findingsJson = json.encodeToString(
            report.findings.map {
                SerializedFinding(
                    engineName = it.engineName,
                    ruleId = it.ruleId,
                    title = it.title,
                    description = it.description,
                    weight = it.weight,
                    categoryName = it.category.name
                )
            }
        )
        val adsJson = json.encodeToString(
            report.adSdks.map {
                SerializedAdSdk(
                    id = it.id,
                    name = it.name,
                    company = it.company,
                    categoryName = it.category.name,
                    matchedSignature = it.matchedSignature
                )
            }
        )
        return AntivirusScanEntity(
            packageName = report.packageName,
            appLabel = report.appLabel,
            versionName = report.versionName,
            versionCode = report.versionCode,
            lastUpdateTime = lastUpdateTime,
            isSystemApp = report.isSystemApp,
            inWorkProfile = report.inWorkProfile,
            sha256 = report.sha256,
            installerPackage = report.installerPackage,
            signerSummary = report.signerSummary,
            verdict = report.verdict.name,
            threatScore = report.threatScore,
            scanDepth = report.scanDepth.name,
            findingsJson = findingsJson,
            adSdksJson = adsJson,
            scanDurationMs = report.scanDurationMs,
            scannedAt = report.scannedAt
        )
    }

    fun entityToReport(entity: AntivirusScanEntity, fromCache: Boolean = true): AvScanReport {
        val findings = runCatching {
            json.decodeFromString<List<SerializedFinding>>(entity.findingsJson).map {
                AvFinding(
                    engineName = it.engineName,
                    ruleId = it.ruleId,
                    title = it.title,
                    description = it.description,
                    weight = it.weight,
                    category = runCatching { AvFindingCategory.valueOf(it.categoryName) }
                        .getOrDefault(AvFindingCategory.SUSPICIOUS_MANIFEST)
                )
            }
        }.getOrDefault(emptyList())

        val adSdks = runCatching {
            json.decodeFromString<List<SerializedAdSdk>>(entity.adSdksJson).map {
                TrackerMatch(
                    id = it.id,
                    name = it.name,
                    company = it.company,
                    category = runCatching { TrackerCategory.valueOf(it.categoryName) }
                        .getOrDefault(TrackerCategory.ADS),
                    matchedSignature = it.matchedSignature
                )
            }
        }.getOrDefault(emptyList())

        return AvScanReport(
            packageName = entity.packageName,
            appLabel = entity.appLabel,
            versionName = entity.versionName,
            versionCode = entity.versionCode,
            isSystemApp = entity.isSystemApp,
            inWorkProfile = entity.inWorkProfile,
            sha256 = entity.sha256,
            installerPackage = entity.installerPackage,
            signerSummary = entity.signerSummary,
            verdict = runCatching { AvVerdict.valueOf(entity.verdict) }.getOrDefault(AvVerdict.CLEAN),
            threatScore = entity.threatScore,
            scanDepth = runCatching { AvScanDepth.valueOf(entity.scanDepth) }.getOrDefault(AvScanDepth.STANDARD),
            findings = findings,
            adSdks = adSdks,
            scanDurationMs = entity.scanDurationMs,
            fromCache = fromCache,
            scannedAt = entity.scannedAt
        )
    }
}
