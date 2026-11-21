package com.example

import android.app.Application
import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.av.AhoCorasickMatcher
import com.example.data.local.av.OpenSourceAvRules
import com.example.data.local.db.AntivirusScanEntity
import com.example.data.local.db.EscudoDatabase
import com.example.data.local.db.FirewallRuleEntity
import com.example.data.local.tracker.TrackerScanner
import com.example.data.local.tracker.TrackerSignatureDatabase
import com.example.data.system.AppInspectorRepository
import com.example.data.system.WorkProfileManager
import com.example.domain.model.AvScanDepth
import com.example.domain.model.AvScanReport
import com.example.domain.model.AvVerdict
import com.example.service.AppPackageMonitorReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Escudo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Escudo", appName)
    }

    @Test
    fun `tracker scanner detects embedded SDK signature in APK dex`() = runTest {
        val tempApk = File.createTempFile("escudo_test_", ".apk")
        try {
            ZipOutputStream(tempApk.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("classes.dex"))
                val fakeDexBytes = ByteArrayOutputStream().apply {
                    write("dex\n039\u0000".toByteArray(Charsets.US_ASCII))
                    write("Lcom/appsflyer/AppsFlyerLib;".toByteArray(Charsets.US_ASCII))
                    write("Lcom/google/firebase/analytics/FirebaseAnalytics;".toByteArray(Charsets.US_ASCII))
                }.toByteArray()
                zip.write(fakeDexBytes)
                zip.closeEntry()
            }

            val scanner = TrackerScanner()
            val matches = scanner.scanApk(tempApk.absolutePath)
            assertTrue(matches.any { it.id == "appsflyer" })
            assertTrue(matches.any { it.id == "google_firebase_analytics" })
            assertTrue(TrackerSignatureDatabase.signatures.size >= 20)
        } finally {
            tempApk.delete()
        }
    }

    @Test
    fun `aho corasick open source AV matcher detects malware and adware in single pass`() {
        val pairs = (OpenSourceAvRules.dexRules + OpenSourceAvRules.adwareAndAdsRules).flatMap { rule ->
            rule.patterns.map { it.toByteArray(Charsets.US_ASCII) to rule }
        }
        val matcher = AhoCorasickMatcher(pairs)
        val payload = (
            "dex\n039\u0000Ldalvik/system/DexClassLoader;" +
                "Lcom/airpush/android/Airpush;" +
                "Lcom/bytedance/sdk/openadsdk/TTAdSdk;"
            ).toByteArray(Charsets.US_ASCII)

        val matchedIds = linkedSetOf<String>()
        matcher.scanStream(ByteArrayInputStream(payload), ByteArray(64)) { rule ->
            matchedIds.add(rule.id)
        }

        assertTrue(matchedIds.contains("YARA_DYNAMIC_DEX_LOADING"))
        assertTrue(matchedIds.contains("CLAM_HIDDEN_ADS_GHOST"))
        assertTrue(matchedIds.contains("ADS_BYTEDANCE_PANGLE"))
    }

    @Test
    fun `room firewall and antivirus DAOs persist rules and cached verdicts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EscudoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val fwDao = db.firewallDao()
            fwDao.upsertRule(
                FirewallRuleEntity(
                    packageName = "com.test.tracker",
                    appLabel = "Tracker App",
                    uid = 10245,
                    isSystemApp = false,
                    allowWifi = true,
                    allowCellular = true
                )
            )
            fwDao.updateWifiRule("com.test.tracker", false)
            val rules = fwDao.observeAllRules().first()
            assertEquals(1, rules.size)
            assertFalse(rules.first().allowWifi)
            assertTrue(rules.first().allowCellular)

            val avDao = db.antivirusDao()
            avDao.upsertScan(
                AntivirusScanEntity(
                    packageName = "com.test.malware",
                    appLabel = "Suspicious App",
                    versionName = "1.0",
                    versionCode = 1L,
                    lastUpdateTime = 1000L,
                    isSystemApp = false,
                    inWorkProfile = false,
                    sha256 = "abc123",
                    installerPackage = null,
                    signerSummary = "CN=Android Debug",
                    verdict = AvVerdict.HIGH_RISK_MALWARE.name,
                    threatScore = 72,
                    scanDepth = AvScanDepth.ULTRA.name,
                    findingsJson = "[]",
                    adSdksJson = "[]",
                    scanDurationMs = 15L,
                    scannedAt = 2000L
                )
            )
            val savedScan = avDao.getScanForPackage("com.test.malware")
            assertEquals(AvVerdict.HIGH_RISK_MALWARE.name, savedScan?.verdict)
            assertEquals(72, savedScan?.threatScore)
        } finally {
            db.close()
        }
    }

    @Test
    fun `threat notification contains required Spanish prompt and Desinstalar and Analizar actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val receiver = AppPackageMonitorReceiver()
        val report = AvScanReport(
            packageName = "com.malicious.sample",
            appLabel = "App Sospechosa",
            versionName = "1.0",
            versionCode = 1L,
            isSystemApp = false,
            inWorkProfile = false,
            sha256 = "deadbeef",
            installerPackage = null,
            signerSummary = "CN=Unknown",
            verdict = AvVerdict.MALWARE_DETECTED,
            threatScore = 88,
            scanDepth = AvScanDepth.MAXIMUM,
            findings = emptyList(),
            adSdks = emptyList(),
            scanDurationMs = 12L,
            fromCache = false,
            scannedAt = 123456L
        )

        val notification = receiver.buildThreatNotification(context, report)
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertEquals(
            "He detectado una aplicación con posible riesgo o virus detectado. Por favor desinstálala o analízela.",
            text
        )
        assertNotNull(notification.actions)
        val titles = notification.actions.map { it.title.toString() }
        assertEquals(listOf("Desinstalar", "Analizar"), titles)
    }

    @Test
    fun `openTrackerWebSearch and launchUninstallApp dispatch proper browser and ACTION_DELETE intents`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = AppInspectorRepository(
            context = app,
            workProfileManager = WorkProfileManager(app),
            trackerScanner = TrackerScanner()
        )

        val webOpened = repo.openTrackerWebSearch(
            trackerName = "Google Firebase Analytics",
            trackerCompany = "Google LLC",
            appLabel = "MiApp",
            packageName = "com.example.miapp"
        )
        assertTrue(webOpened)
        val shadowApp = Shadows.shadowOf(app)
        val browserIntent = shadowApp.nextStartedActivity
        assertNotNull(browserIntent)
        assertEquals(Intent.ACTION_VIEW, browserIntent.action)
        val uriString = browserIntent.data?.toString().orEmpty()
        assertTrue(uriString.startsWith("https://www.google.com/search?q="))
        assertTrue(uriString.contains("rastreador"))
        assertTrue(uriString.contains("Google"))

        val uninstallOpened = repo.launchUninstallApp("com.example.miapp")
        assertTrue(uninstallOpened)
        val uninstallIntent = shadowApp.nextStartedActivity
        assertNotNull(uninstallIntent)
        assertEquals(Intent.ACTION_DELETE, uninstallIntent.action)
        assertEquals("package:com.example.miapp", uninstallIntent.data?.toString())
    }
}
