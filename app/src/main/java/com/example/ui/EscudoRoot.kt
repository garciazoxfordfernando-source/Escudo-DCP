package com.example.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.net.Uri
import com.example.R
import com.example.ThreatNotificationPayload
import com.example.service.AppPackageMonitorReceiver
import com.example.ui.components.BiometricLockScreen
import com.example.ui.components.UninstallPromptDialog
import com.example.ui.navigation.AntivirusRoute
import com.example.ui.navigation.AppsRoute
import com.example.ui.navigation.EscudoRoute
import com.example.ui.navigation.FirewallRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.TrackersRoute
import com.example.ui.navigation.WorkProfileRoute
import com.example.ui.screens.AntivirusScreen
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.FirewallScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrackersScreen
import com.example.ui.screens.WorkProfileScreen
import com.example.ui.viewmodel.SecurityGateViewModel

private data class TopDestination(
    val route: EscudoRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(AppsRoute, R.string.nav_apps, Icons.Rounded.Apps, Icons.Outlined.Apps, "nav_apps"),
    TopDestination(WorkProfileRoute, R.string.nav_work_profile, Icons.Rounded.Work, Icons.Outlined.WorkOutline, "nav_work_profile"),
    TopDestination(AntivirusRoute, R.string.nav_antivirus, Icons.Rounded.GppGood, Icons.Outlined.GppGood, "nav_antivirus"),
    TopDestination(FirewallRoute, R.string.nav_firewall, Icons.Rounded.Security, Icons.Outlined.Security, "nav_firewall"),
    TopDestination(TrackersRoute, R.string.nav_trackers, Icons.Rounded.BugReport, Icons.Outlined.BugReport, "nav_trackers"),
    TopDestination(SettingsRoute, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
)

@Composable
fun EscudoRoot(
    widthSizeClass: WindowWidthSizeClass,
    threatNotification: ThreatNotificationPayload? = null,
    onClearThreatNotification: () -> Unit = {},
    securityGateViewModel: SecurityGateViewModel = hiltViewModel()
) {
    val securityConfig by securityGateViewModel.securityConfig.collectAsStateWithLifecycle()
    val isUnlocked by securityGateViewModel.isUnlocked.collectAsStateWithLifecycle()
    val gateEvaluated by securityGateViewModel.gateEvaluated.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var biometricError by remember { mutableStateOf<String?>(null) }

    fun launchBiometricPrompt() {
        if (activity == null) {
            securityGateViewModel.onBiometricSuccess()
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val status = BiometricManager.from(context).canAuthenticate(authenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            securityGateViewModel.onBiometricSuccess()
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricError = null
                    securityGateViewModel.onBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricError = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    biometricError = "Credencial no reconocida. Inténtalo de nuevo."
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.biometric_prompt_title))
            .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(gateEvaluated, isUnlocked) {
        if (gateEvaluated && !isUnlocked) {
            launchBiometricPrompt()
        }
    }

    if (!gateEvaluated) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (!isUnlocked) {
        BiometricLockScreen(
            errorText = biometricError,
            onUnlockClick = { launchBiometricPrompt() }
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact
    var pendingAnalyzePackage by remember { mutableStateOf<String?>(null) }

    fun navigateTo(route: EscudoRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(threatNotification) {
        if (threatNotification != null && threatNotification.action == AppPackageMonitorReceiver.ACTION_ANALYZE) {
            pendingAnalyzePackage = threatNotification.packageName
            navigateTo(AntivirusRoute)
            onClearThreatNotification()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (!useRail) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    topDestinations.forEach { dest ->
                        val selected = currentDestination?.hasRoute(dest.route::class) == true
                        val label = stringResource(dest.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = label
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useRail) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(modifier = Modifier.height(12.dp))
                    topDestinations.forEach { dest ->
                        val selected = currentDestination?.hasRoute(dest.route::class) == true
                        val label = stringResource(dest.labelRes)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTo(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                NavHost(
                    navController = navController,
                    startDestination = AppsRoute,
                    modifier = Modifier
                        .widthIn(max = 960.dp)
                        .fillMaxSize()
                ) {
                    composable<AppsRoute> {
                        AppsScreen(
                            snackbarHostState = snackbarHostState,
                            onNavigateToWorkProfile = { navigateTo(WorkProfileRoute) }
                        )
                    }
                    composable<WorkProfileRoute> {
                        WorkProfileScreen(snackbarHostState = snackbarHostState)
                    }
                    composable<AntivirusRoute> {
                        AntivirusScreen(
                            snackbarHostState = snackbarHostState,
                            pendingAnalyzePackage = pendingAnalyzePackage,
                            onPendingAnalyzeConsumed = { pendingAnalyzePackage = null }
                        )
                    }
                    composable<FirewallRoute> {
                        FirewallScreen(snackbarHostState = snackbarHostState)
                    }
                    composable<TrackersRoute> {
                        TrackersScreen(snackbarHostState = snackbarHostState)
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(snackbarHostState = snackbarHostState)
                    }
                }
            }
        }
    }

    if (threatNotification != null && threatNotification.action == AppPackageMonitorReceiver.ACTION_PROMPT_UNINSTALL) {
        UninstallPromptDialog(
            packageName = threatNotification.packageName,
            appLabel = threatNotification.appLabel,
            verdictLabel = threatNotification.verdictLabel,
            onDismiss = onClearThreatNotification,
            onConfirmUninstall = {
                val pkg = threatNotification.packageName
                onClearThreatNotification()
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            },
            onAnalyze = {
                val pkg = threatNotification.packageName
                onClearThreatNotification()
                pendingAnalyzePackage = pkg
                navigateTo(AntivirusRoute)
            }
        )
    }

    if (!securityConfig.onboardingCompleted) {
        AlertDialog(
            onDismissRequest = securityGateViewModel::completeOnboarding,
            title = { Text("Bienvenido a Escudo") },
            text = {
                Text(
                    "Escudo respeta los límites reales de seguridad de Android:\n\n" +
                        "• Antivirus Open-Source: analiza cada app que pase por el dispositivo (con o sin Perfil de Trabajo) buscando virus, código malicioso y Ads.\n" +
                        "• Perfil Principal: sin privilegios especiales solo puede leer apps, permisos y abrir Ajustes del sistema.\n" +
                        "• Perfil de Trabajo (estilo Shelter): al crear el perfil administrado, Escudo es Profile Owner y puede clonar, congelar, ocultar y revocar permisos.\n" +
                        "• Firewall local: utiliza VpnService por UID (Android solo permite 1 VPN activa a la vez)."
                )
            },
            confirmButton = {
                Button(
                    onClick = securityGateViewModel::completeOnboarding,
                    modifier = Modifier.testTag("onboarding_accept_button")
                ) {
                    Text("Entendido")
                }
            }
        )
    }
}
