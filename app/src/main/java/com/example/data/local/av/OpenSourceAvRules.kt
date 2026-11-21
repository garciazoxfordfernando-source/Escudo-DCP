package com.example.data.local.av

import com.example.domain.model.AvFindingCategory
import com.example.domain.model.AvScanDepth

data class AvByteRule(
    val id: String,
    val engineName: String,
    val title: String,
    val description: String,
    val weight: Int,
    val category: AvFindingCategory,
    val minDepth: AvScanDepth,
    val patterns: List<String>
)

data class AvHashSignature(
    val sha256: String,
    val malwareFamily: String,
    val engineName: String
)

object OpenSourceAvRules {

    const val DATABASE_VERSION = "Escudo-OSS-2026.11 (ClamAV + YARA-Android + Exodus)"

    // Hashes de prueba estándar (EICAR y muestras comunitarias de laboratorio)
    val knownMaliciousHashes: Map<String, AvHashSignature> = mapOf(
        "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f" to AvHashSignature(
            sha256 = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
            malwareFamily = "EICAR-Test-Signature",
            engineName = "ClamAV-OSS"
        )
    )

    val dexRules: List<AvByteRule> = listOf(
        // --- Firmas estilo ClamAV / Familias conocidas ---
        AvByteRule(
            id = "CLAM_SPINOK_SPYWARE",
            engineName = "ClamAV-OSS",
            title = "Módulo sospechoso tipo SpinOk / Clipboard-Exfil",
            description = "Coincidencia con rutas de módulos de espionaje que leen el portapapeles y archivos locales en segundo plano.",
            weight = 65,
            category = AvFindingCategory.MALWARE_SIGNATURE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/spinok/", "com/sdk/spinok")
        ),
        AvByteRule(
            id = "CLAM_JOKER_BILLING",
            engineName = "ClamAV-OSS",
            title = "Patrón tipo Joker / Suscripción WAP oculta",
            description = "Indicadores de automatización WebView + interceptación de SMS OTP asociada a fraude de facturación.",
            weight = 65,
            category = AvFindingCategory.MALWARE_SIGNATURE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/joker/billing", "com/wap/autosub", "NotificationListenerService")
        ),
        AvByteRule(
            id = "CLAM_HIDDEN_ADS_GHOST",
            engineName = "ClamAV-OSS",
            title = "SDK de Adware agresivo / Anuncios fuera de contexto",
            description = "Librerías conocidas por lanzar ventanas emergentes fuera de la app, iconos ocultos o notificaciones spam.",
            weight = 38,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/airpush/", "com/leadbolt/", "com/apperhand/", "com/revmob/", "com/startapp/")
        ),
        AvByteRule(
            id = "CLAM_RAT_AHMYTH_SPYNOTE",
            engineName = "ClamAV-OSS",
            title = "Indicador de RAT / Control remoto (AhMyth/SpyNote)",
            description = "Firmas compatibles con troyanos de acceso remoto de código abierto.",
            weight = 75,
            category = AvFindingCategory.MALWARE_SIGNATURE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("ahmyth/mine/king/ahmyth", "com/spynote/", "metasploit/stage/Payload")
        ),

        // --- Heurísticas YARA-Android / AndroGuard ---
        AvByteRule(
            id = "YARA_DYNAMIC_DEX_LOADING",
            engineName = "YARA-Android",
            title = "Carga dinámica de código DEX en memoria",
            description = "Usa DexClassLoader o InMemoryDexClassLoader para cargar código externo fuera del APK verificado.",
            weight = 24,
            category = AvFindingCategory.DYNAMIC_CODE,
            minDepth = AvScanDepth.STANDARD,
            patterns = listOf("Ldalvik/system/DexClassLoader;", "Ldalvik/system/InMemoryDexClassLoader;")
        ),
        AvByteRule(
            id = "YARA_SU_ROOT_EXEC",
            engineName = "YARA-Android",
            title = "Ejecución de comandos shell o binarios su/Magisk",
            description = "Invoca Runtime.exec o rutas de superusuario (/system/bin/su, magisk) para escalar privilegios o evadir análisis.",
            weight = 22,
            category = AvFindingCategory.EXPLOIT_ROOT,
            minDepth = AvScanDepth.STANDARD,
            patterns = listOf("/system/bin/su", "/system/xbin/su", "Lcom/topjohnwu/magisk")
        ),
        AvByteRule(
            id = "YARA_HIDE_LAUNCHER_ICON",
            engineName = "YARA-Android",
            title = "Ocultamiento programático del icono en el launcher",
            description = "Invoca setComponentEnabledSetting para ocultar su propio icono tras ejecutarse (comportamiento típico de dropware).",
            weight = 32,
            category = AvFindingCategory.SPYWARE,
            minDepth = AvScanDepth.STANDARD,
            patterns = listOf("setComponentEnabledSetting")
        ),
        AvByteRule(
            id = "YARA_SMS_SILENT_SEND",
            engineName = "YARA-Android",
            title = "Envío silencioso de SMS desde código",
            description = "Utiliza SmsManager.sendTextMessage / sendMultipartTextMessage directamente.",
            weight = 28,
            category = AvFindingCategory.BANKING_TROJAN,
            minDepth = AvScanDepth.STANDARD,
            patterns = listOf("SmsManager;->sendTextMessage", "SmsManager;->sendMultipartTextMessage")
        ),
        AvByteRule(
            id = "YARA_ACCESSIBILITY_GLOBAL_ACTION",
            engineName = "YARA-Android",
            title = "Automatización por Servicio de Accesibilidad",
            description = "Usa performGlobalAction o AccessibilityNodeInfo.performAction; técnica común en troyanos bancarios para pulsar botones sin permiso.",
            weight = 24,
            category = AvFindingCategory.BANKING_TROJAN,
            minDepth = AvScanDepth.MAXIMUM,
            patterns = listOf("performGlobalAction", "AccessibilityNodeInfo;->performAction")
        ),
        AvByteRule(
            id = "YARA_TOR_ONION_C2",
            engineName = "YARA-Android",
            title = "Referencias a nodos ocultos .onion / C2",
            description = "Contiene cadenas de red Tor (.onion) o proxies ocultos potencialmente usados para comando y control.",
            weight = 30,
            category = AvFindingCategory.DYNAMIC_CODE,
            minDepth = AvScanDepth.MAXIMUM,
            patterns = listOf(".onion/", "onion.ly", "onion.ws")
        ),
        AvByteRule(
            id = "YARA_TELEGRAM_BOT_EXFIL",
            engineName = "YARA-Android",
            title = "Posible exfiltración vía Telegram Bot API",
            description = "Referencias directas a api.telegram.org/bot dentro del DEX (frecuente en stealers ligeros).",
            weight = 36,
            category = AvFindingCategory.SPYWARE,
            minDepth = AvScanDepth.MAXIMUM,
            patterns = listOf("api.telegram.org/bot")
        ),
        AvByteRule(
            id = "ULTRA_REFLECTION_EVASION",
            engineName = "Koodous-Heurística",
            title = "Ofuscación por reflexión + carga de librerías nativas",
            description = "Combina llamadas por reflexión con carga directa de rutas nativas (System.load) para dificultar auditorías estáticas.",
            weight = 18,
            category = AvFindingCategory.DYNAMIC_CODE,
            minDepth = AvScanDepth.ULTRA,
            patterns = listOf("Ljava/lang/System;->load(", "Ldalvik/system/DexFile;")
        ),
        AvByteRule(
            id = "ULTRA_RANSOM_CRYPTO_WIPE",
            engineName = "Koodous-Heurística",
            title = "Patrón de cifrado masivo / borrado de archivos externos",
            description = "Combina cifrado local con manipulación directa de almacenamiento compartido externo.",
            weight = 25,
            category = AvFindingCategory.EXPLOIT_ROOT,
            minDepth = AvScanDepth.ULTRA,
            patterns = listOf("Environment;->getExternalStorageDirectory", "DevicePolicyManager;->resetPassword")
        )
    )

    val adwareAndAdsRules: List<AvByteRule> = listOf(
        AvByteRule(
            id = "ADS_MINTEGRAL",
            engineName = "Exodus-Ads",
            title = "Mintegral Ads SDK",
            description = "Red publicitaria móvil con recopilación extensiva de telemetría del dispositivo.",
            weight = 14,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/mbridge/msdk")
        ),
        AvByteRule(
            id = "ADS_BYTEDANCE_PANGLE",
            engineName = "Exodus-Ads",
            title = "ByteDance Pangle Ads",
            description = "Red publicitaria de alto perfilado integrada en el DEX.",
            weight = 12,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/bytedance/sdk/openadsdk")
        ),
        AvByteRule(
            id = "ADS_APPLOVIN",
            engineName = "Exodus-Ads",
            title = "AppLovin MAX Ads",
            description = "Mediación publicitaria y anuncios intersticiales/vídeo.",
            weight = 10,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/applovin/sdk", "com/applovin/mediation")
        ),
        AvByteRule(
            id = "ADS_ADMOB",
            engineName = "Exodus-Ads",
            title = "Google AdMob",
            description = "SDK publicitario estándar de Google.",
            weight = 6,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/google/android/gms/ads")
        ),
        AvByteRule(
            id = "ADS_UNITY",
            engineName = "Exodus-Ads",
            title = "Unity Ads",
            description = "Red de anuncios en vídeo y recompensas.",
            weight = 8,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/unity3d/ads")
        ),
        AvByteRule(
            id = "ADS_IRONSOURCE",
            engineName = "Exodus-Ads",
            title = "ironSource Mediation",
            description = "Mediación de anuncios móviles y offerwalls.",
            weight = 10,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/ironsource/mediationsdk")
        ),
        AvByteRule(
            id = "ADS_META_AUDIENCE",
            engineName = "Exodus-Ads",
            title = "Meta Audience Network",
            description = "Red de publicidad de Meta con vinculación de perfilado.",
            weight = 12,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/facebook/ads")
        ),
        AvByteRule(
            id = "ADS_VUNGLE_INMOBI",
            engineName = "Exodus-Ads",
            title = "Liftoff Vungle / InMobi",
            description = "Redes publicitarias de terceros.",
            weight = 10,
            category = AvFindingCategory.ADWARE,
            minDepth = AvScanDepth.FAST,
            patterns = listOf("com/vungle/ads", "com/inmobi/ads")
        )
    )

    fun totalRuleCount(): Int = dexRules.size + adwareAndAdsRules.size + knownMaliciousHashes.size + 4
}
