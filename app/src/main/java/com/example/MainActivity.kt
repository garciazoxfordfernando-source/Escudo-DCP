package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AppPackageMonitorReceiver
import com.example.ui.EscudoRoot
import com.example.ui.theme.EscudoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@Immutable
data class ThreatNotificationPayload(
    val packageName: String,
    val appLabel: String,
    val verdictLabel: String,
    val action: String
)

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val threatPayloadFlow = MutableStateFlow<ThreatNotificationPayload?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleThreatIntent(intent)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val threatPayload by threatPayloadFlow.collectAsStateWithLifecycle()
            EscudoTheme {
                EscudoRoot(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    threatNotification = threatPayload,
                    onClearThreatNotification = { threatPayloadFlow.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleThreatIntent(intent)
    }

    private fun handleThreatIntent(intent: Intent?) {
        val pkg = intent?.getStringExtra(AppPackageMonitorReceiver.EXTRA_THREAT_PACKAGE)
        if (pkg.isNullOrBlank()) return
        val label = intent.getStringExtra(AppPackageMonitorReceiver.EXTRA_THREAT_LABEL) ?: pkg
        val verdict = intent.getStringExtra(AppPackageMonitorReceiver.EXTRA_THREAT_VERDICT) ?: "Riesgo"
        val action = intent.getStringExtra(AppPackageMonitorReceiver.EXTRA_THREAT_ACTION)
            ?: AppPackageMonitorReceiver.ACTION_PROMPT_UNINSTALL
        threatPayloadFlow.value = ThreatNotificationPayload(
            packageName = pkg,
            appLabel = label,
            verdictLabel = verdict,
            action = action
        )
    }
}
