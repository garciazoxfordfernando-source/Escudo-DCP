package com.example.data.local.tracker

import com.example.domain.model.TrackerCategory
import com.example.domain.model.TrackerMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

data class TrackerSignature(
    val id: String,
    val name: String,
    val company: String,
    val category: TrackerCategory,
    val packageSignatures: List<String>
)

object TrackerSignatureDatabase {
    val signatures: List<TrackerSignature> = listOf(
        TrackerSignature(
            id = "google_firebase_analytics",
            name = "Google Firebase Analytics",
            company = "Google",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/google/firebase/analytics", "com/google/android/gms/measurement")
        ),
        TrackerSignature(
            id = "google_crashlytics",
            name = "Firebase Crashlytics",
            company = "Google",
            category = TrackerCategory.TELEMETRY,
            packageSignatures = listOf("com/google/firebase/crashlytics", "com/crashlytics/android")
        ),
        TrackerSignature(
            id = "google_admob",
            name = "Google AdMob",
            company = "Google",
            category = TrackerCategory.ADS,
            packageSignatures = listOf("com/google/android/gms/ads", "com/google/ads/mediation")
        ),
        TrackerSignature(
            id = "meta_facebook_analytics",
            name = "Facebook / Meta SDK",
            company = "Meta",
            category = TrackerCategory.SOCIAL,
            packageSignatures = listOf("com/facebook/appevents", "com/facebook/ads", "com/facebook/FacebookSdk")
        ),
        TrackerSignature(
            id = "appsflyer",
            name = "AppsFlyer",
            company = "AppsFlyer",
            category = TrackerCategory.ATTRIBUTION,
            packageSignatures = listOf("com/appsflyer")
        ),
        TrackerSignature(
            id = "adjust",
            name = "Adjust",
            company = "Adjust",
            category = TrackerCategory.ATTRIBUTION,
            packageSignatures = listOf("com/adjust/sdk")
        ),
        TrackerSignature(
            id = "branch",
            name = "Branch Metrics",
            company = "Branch",
            category = TrackerCategory.ATTRIBUTION,
            packageSignatures = listOf("io/branch/referral")
        ),
        TrackerSignature(
            id = "kochava",
            name = "Kochava",
            company = "Kochava",
            category = TrackerCategory.ATTRIBUTION,
            packageSignatures = listOf("com/kochava/tracker")
        ),
        TrackerSignature(
            id = "applovin",
            name = "AppLovin MAX",
            company = "AppLovin",
            category = TrackerCategory.ADS,
            packageSignatures = listOf("com/applovin/sdk", "com/applovin/mediation")
        ),
        TrackerSignature(
            id = "unity_ads",
            name = "Unity Ads",
            company = "Unity",
            category = TrackerCategory.ADS,
            packageSignatures = listOf("com/unity3d/ads", "com/unity3d/services")
        ),
        TrackerSignature(
            id = "ironsource",
            name = "ironSource",
            company = "Unity / ironSource",
            category = TrackerCategory.ADS,
            packageSignatures = listOf("com/ironsource/mediationsdk")
        ),
        TrackerSignature(
            id = "pangle",
            name = "ByteDance Pangle",
            company = "ByteDance",
            category = TrackerCategory.ADS,
            packageSignatures = listOf("com/bytedance/sdk/openadsdk")
        ),
        TrackerSignature(
            id = "mixpanel",
            name = "Mixpanel",
            company = "Mixpanel",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/mixpanel/android")
        ),
        TrackerSignature(
            id = "amplitude",
            name = "Amplitude",
            company = "Amplitude",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/amplitude/api", "com/amplitude/android")
        ),
        TrackerSignature(
            id = "sentry",
            name = "Sentry",
            company = "Sentry",
            category = TrackerCategory.TELEMETRY,
            packageSignatures = listOf("io/sentry/android")
        ),
        TrackerSignature(
            id = "bugsnag",
            name = "Bugsnag",
            company = "SmartBear",
            category = TrackerCategory.TELEMETRY,
            packageSignatures = listOf("com/bugsnag/android")
        ),
        TrackerSignature(
            id = "onesignal",
            name = "OneSignal",
            company = "OneSignal",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/onesignal")
        ),
        TrackerSignature(
            id = "braze",
            name = "Braze (Appboy)",
            company = "Braze",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/braze", "com/appboy")
        ),
        TrackerSignature(
            id = "yandex_appmetrica",
            name = "AppMetrica",
            company = "Yandex",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/yandex/metrica", "io/appmetrica/analytics")
        ),
        TrackerSignature(
            id = "huawei_hms_analytics",
            name = "Huawei HMS Analytics",
            company = "Huawei",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/huawei/hms/analytics")
        ),
        TrackerSignature(
            id = "clevertap",
            name = "CleverTap",
            company = "CleverTap",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/clevertap/android")
        ),
        TrackerSignature(
            id = "moengage",
            name = "MoEngage",
            company = "MoEngage",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/moengage")
        ),
        TrackerSignature(
            id = "flurry",
            name = "Flurry Analytics",
            company = "Yahoo",
            category = TrackerCategory.ANALYTICS,
            packageSignatures = listOf("com/flurry/android")
        ),
        TrackerSignature(
            id = "vungle",
            name = "Liftoff / Vungle",
            company = "Liftoff",
            category = TrackerCategory.ADS,
            packageSignatures = listOf("com/vungle/ads", "com/vungle/warren")
        ),
        TrackerSignature(
            id = "inmobi",
            name = "InMobi",
            company = "InMobi",
            category = TrackerCategory.ADS,
            packageSignatures = listOf("com/inmobi/ads", "com/inmobi/sdk")
        )
    )
}

@Singleton
class TrackerScanner @Inject constructor() {

    private val preparedSignatures: List<Pair<TrackerSignature, List<ByteArray>>> =
        TrackerSignatureDatabase.signatures.map { sig ->
            val patterns = sig.packageSignatures.flatMap { pkg ->
                listOf(
                    pkg.toByteArray(Charsets.US_ASCII),
                    pkg.replace('/', '.').toByteArray(Charsets.US_ASCII)
                )
            }
            sig to patterns
        }

    suspend fun scanApk(apkPath: String?): List<TrackerMatch> = withContext(Dispatchers.IO) {
        if (apkPath.isNullOrBlank()) return@withContext emptyList()
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return@withContext emptyList()

        val found = linkedMapOf<String, TrackerMatch>()
        runCatching {
            ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (
                        (name.startsWith("classes") && name.endsWith(".dex")) ||
                        name == "AndroidManifest.xml"
                    ) {
                        zip.getInputStream(entry).use { stream ->
                            scanStreamForSignatures(stream, found)
                        }
                    }
                    if (found.size == preparedSignatures.size) break
                }
            }
        }
        found.values.toList()
    }

    private fun scanStreamForSignatures(
        stream: InputStream,
        found: MutableMap<String, TrackerMatch>
    ) {
        val chunkSize = 128 * 1024
        val overlap = 128
        val buffer = ByteArray(chunkSize + overlap)
        var carry = 0

        while (true) {
            val bytesRead = stream.read(buffer, carry, chunkSize)
            if (bytesRead <= 0) break
            val totalLen = carry + bytesRead

            for ((sig, patterns) in preparedSignatures) {
                if (found.containsKey(sig.id)) continue
                for (pattern in patterns) {
                    if (containsBytes(buffer, totalLen, pattern)) {
                        found[sig.id] = TrackerMatch(
                            id = sig.id,
                            name = sig.name,
                            company = sig.company,
                            category = sig.category,
                            matchedSignature = String(pattern, Charsets.US_ASCII)
                        )
                        break
                    }
                }
            }

            if (found.size == preparedSignatures.size) break
            carry = minOf(overlap, totalLen)
            System.arraycopy(buffer, totalLen - carry, buffer, 0, carry)
        }
    }

    private fun containsBytes(haystack: ByteArray, haystackLen: Int, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystackLen < needle.size) return false
        val first = needle[0]
        val maxStart = haystackLen - needle.size
        var i = 0
        while (i <= maxStart) {
            if (haystack[i] != first) {
                i++
                continue
            }
            var j = 1
            while (j < needle.size && haystack[i + j] == needle[j]) {
                j++
            }
            if (j == needle.size) return true
            i++
        }
        return false
    }
}
