package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.prefs.SecurityPreferencesRepository
import com.example.data.repository.AntivirusRepository
import com.example.data.system.WorkProfileManager
import com.example.domain.model.AvScanReport
import com.example.domain.model.AvVerdict
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AppPackageMonitorReceiver : BroadcastReceiver() {

    @Inject
    lateinit var securityPreferencesRepository: SecurityPreferencesRepository

    @Inject
    lateinit var antivirusRepository: AntivirusRepository

    @Inject
    lateinit var workProfileManager: WorkProfileManager

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED && action != Intent.ACTION_PACKAGE_REPLACED) return
        val pkgName = intent.data?.schemeSpecificPart ?: return
        if (pkgName == context.packageName) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val config = securityPreferencesRepository.securityConfig.first()
                // Si el usuario activó el ahorro de batería o desactivó el análisis automático, salir sin gastar CPU.
                if (config.avBatterySaverMode || !config.avAutoScanEnabled) {
                    return@launch
                }

                val inWork = workProfileManager.getStatus().isProfileOwner
                val report = antivirusRepository.scanPackageName(
                    packageName = pkgName,
                    depth = config.avScanDepth,
                    inWorkProfile = inWork,
                    useCache = false,
                    includeAdsAudit = config.avAutoAnalyzeAds,
                    forceRefresh = true
                )

                if (report != null && (
                        report.verdict == AvVerdict.MALWARE_DETECTED ||
                        report.verdict == AvVerdict.HIGH_RISK_MALWARE ||
                        report.verdict == AvVerdict.RISK
                    )
                ) {
                    notifyThreatDetected(context, report)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun notifyThreatDetected(context: Context, report: AvScanReport) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas Antivirus Escudo",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifica amenazas o alto riesgo detectado al instalar o actualizar apps."
        }
        nm.createNotificationChannel(channel)

        val notification = buildThreatNotification(context, report)
        nm.notify(report.packageName.hashCode(), notification)
    }

    internal fun buildThreatNotification(context: Context, report: AvScanReport): Notification {
        val baseReqCode = report.packageName.hashCode()

        // Tocar la notificación abre el diálogo "¿Quieres desinstalar esta aplicación?" con botones Desinstalar y Analizar
        val promptIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAT_PACKAGE, report.packageName)
            putExtra(EXTRA_THREAT_LABEL, report.appLabel)
            putExtra(EXTRA_THREAT_VERDICT, report.verdict.label)
            putExtra(EXTRA_THREAT_SCORE, report.threatScore)
            putExtra(EXTRA_THREAT_ACTION, ACTION_PROMPT_UNINSTALL)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            baseReqCode,
            promptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción "Desinstalar": lanza directamente el desinstalador nativo de Android ("¿Quieres desinstalar esta aplicación?")
        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", report.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val uninstallPendingIntent = PendingIntent.getActivity(
            context,
            baseReqCode + 1,
            uninstallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción "Analizar": abre Escudo en el escáner Antivirus con el análisis detallado de esta aplicación
        val analyzeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_THREAT_PACKAGE, report.packageName)
            putExtra(EXTRA_THREAT_LABEL, report.appLabel)
            putExtra(EXTRA_THREAT_VERDICT, report.verdict.label)
            putExtra(EXTRA_THREAT_SCORE, report.threatScore)
            putExtra(EXTRA_THREAT_ACTION, ACTION_ANALYZE)
        }
        val analyzePendingIntent = PendingIntent.getActivity(
            context,
            baseReqCode + 2,
            analyzeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${report.verdict.label}: ${report.appLabel}")
            .setContentText(ALERT_MESSAGE)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$ALERT_MESSAGE\n\nApp: ${report.appLabel} (${report.packageName}) · Riesgo ${report.threatScore}/100")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(contentPendingIntent)
            .addAction(0, ACTION_LABEL_UNINSTALL, uninstallPendingIntent)
            .addAction(0, ACTION_LABEL_ANALYZE, analyzePendingIntent)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        internal const val CHANNEL_ID = "escudo_av_alerts"
        internal const val ALERT_MESSAGE =
            "He detectado una aplicación con posible riesgo o virus detectado. Por favor desinstálala o analízela."
        internal const val ACTION_LABEL_UNINSTALL = "Desinstalar"
        internal const val ACTION_LABEL_ANALYZE = "Analizar"

        const val EXTRA_THREAT_PACKAGE = "extra_threat_package"
        const val EXTRA_THREAT_LABEL = "extra_threat_label"
        const val EXTRA_THREAT_VERDICT = "extra_threat_verdict"
        const val EXTRA_THREAT_SCORE = "extra_threat_score"
        const val EXTRA_THREAT_ACTION = "extra_threat_action"

        const val ACTION_PROMPT_UNINSTALL = "prompt_uninstall"
        const val ACTION_ANALYZE = "analyze"
    }
}
