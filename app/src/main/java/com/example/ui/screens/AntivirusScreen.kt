package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.AvScanDepth
import com.example.domain.model.AvScanReport
import com.example.domain.model.AvVerdict
import com.example.domain.model.TrackerMatch
import com.example.ui.components.AppIconBadge
import com.example.ui.components.StatusTag
import com.example.ui.components.TacticalNoticeBanner
import com.example.ui.components.UninstallPromptDialog
import com.example.ui.viewmodel.AntivirusTab
import com.example.ui.viewmodel.AntivirusViewModel
import com.example.ui.viewmodel.AvFilter

@Composable
fun AntivirusScreen(
    snackbarHostState: SnackbarHostState,
    pendingAnalyzePackage: String? = null,
    onPendingAnalyzeConsumed: () -> Unit = {},
    viewModel: AntivirusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(pendingAnalyzePackage) {
        if (!pendingAnalyzePackage.isNullOrBlank()) {
            viewModel.openOrAnalyzePackage(pendingAnalyzePackage)
            onPendingAnalyzeConsumed()
        }
    }

    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val threatsCount = state.reports.count {
        it.verdict == AvVerdict.MALWARE_DETECTED || it.verdict == AvVerdict.HIGH_RISK_MALWARE
    }
    val riskCount = state.reports.count { it.verdict == AvVerdict.RISK }
    val adsAppsCount = state.reports.count { it.adSdks.isNotEmpty() }
    val cleanCount = state.reports.count { it.verdict == AvVerdict.CLEAN }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Motor Antivirus Open-Source",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${state.databaseVersion} · ${state.ruleCount} reglas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusTag(
                text = state.securityConfig.avScanDepth.label.uppercase(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Card del Motor Antivirus
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                1.dp,
                if (threatsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (threatsCount > 0) Icons.Rounded.GppMaybe else Icons.Rounded.GppGood,
                            contentDescription = null,
                            tint = if (threatsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = when {
                                    state.isScanning && state.isAdsSweepRunning -> "Analizando Ads automáticamente..."
                                    state.isScanning -> "Análisis ${state.securityConfig.avScanDepth.label} en curso"
                                    threatsCount > 0 -> "$threatsCount app(s) con riesgo alto o código malicioso"
                                    state.reports.isEmpty() -> "Listo para analizar todas las apps"
                                    else -> "Sin código malicioso crítico detectado"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (state.securityConfig.avBatterySaverMode) {
                                    "Ahorro de batería activo · Análisis automático desactivado"
                                } else {
                                    "Protección activa en perfil principal y de trabajo (${state.reports.size} analizadas)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.isScanning && state.totalCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (state.scannedCount.toFloat() / state.totalCount.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.scannedCount}/${state.totalCount} · ${state.currentAppName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isScanning) {
                        Button(
                            onClick = viewModel::cancelScan,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("av_cancel_scan_button")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startFullScan(forceRefresh = false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("av_start_scan_button")
                        ) {
                            Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analizar AV")
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::runAutoAdsAnalysis,
                        enabled = !state.isScanning,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(48.dp)
                            .testTag("av_auto_ads_button")
                    ) {
                        Icon(Icons.Rounded.AdsClick, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analizar Ads", maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AntivirusTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = state.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = AntivirusTab.entries.size),
                    modifier = Modifier.testTag("av_tab_${tab.name.lowercase()}")
                ) {
                    Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (state.selectedTab) {
            AntivirusTab.SCANNER -> AvScannerTabContent(
                reports = state.filteredReports,
                searchQuery = state.searchQuery,
                selectedFilter = state.selectedFilter,
                threatsCount = threatsCount,
                riskCount = riskCount,
                adsAppsCount = adsAppsCount,
                cleanCount = cleanCount,
                onSearchChange = viewModel::updateSearch,
                onFilterSelect = viewModel::selectFilter,
                onOpenReport = viewModel::openReport
            )

            AntivirusTab.ADS_AUDIT -> AvAdsAuditTabContent(
                reports = state.reports.filter { it.adSdks.isNotEmpty() || state.isAdsSweepRunning }
                    .sortedByDescending { it.adSdks.size },
                autoAnalyzeAds = state.securityConfig.avAutoAnalyzeAds,
                isScanning = state.isScanning,
                onToggleAutoAds = viewModel::setAutoAnalyzeAds,
                onRunAutoAds = viewModel::runAutoAdsAnalysis,
                onOpenReport = viewModel::openReport
            )

            AntivirusTab.ENGINE_MENU -> AvEngineMenuTabContent(
                selectedDepth = state.securityConfig.avScanDepth,
                batterySaverMode = state.securityConfig.avBatterySaverMode,
                autoAnalyzeAds = state.securityConfig.avAutoAnalyzeAds,
                scanSystemApps = state.securityConfig.avScanSystemApps,
                useIncrementalCache = state.securityConfig.avUseIncrementalCache,
                databaseVersion = state.databaseVersion,
                ruleCount = state.ruleCount,
                onSelectDepth = viewModel::setScanDepth,
                onToggleBatterySaver = viewModel::setBatterySaverMode,
                onToggleAutoAds = viewModel::setAutoAnalyzeAds,
                onToggleSystemApps = viewModel::setScanSystemApps,
                onToggleIncrementalCache = viewModel::setIncrementalCache,
                onClearCache = viewModel::clearScanCache,
                onForceDeepRescan = { viewModel.startFullScan(forceRefresh = true) }
            )
        }
    }

    val selected = state.selectedReport
    if (selected != null) {
        AvReportDetailSheet(
            report = selected,
            isProfileOwner = state.workProfileStatus.isProfileOwner,
            onDismiss = viewModel::dismissReport,
            onRescan = { viewModel.rescanPackage(selected.packageName) },
            onUninstall = { viewModel.requestUninstall(selected) },
            onIsolateWork = { viewModel.isolateInWorkProfile(selected.packageName) },
            onFreeze = { viewModel.freezeApp(selected.packageName) },
            onOpenSettings = { viewModel.openSystemSettings(selected.packageName) },
            onVerifySdk = { sdk -> viewModel.verifySdkInBrowser(selected, sdk) }
        )
    }

    val pendingUninstall = state.pendingUninstallReport
    if (pendingUninstall != null) {
        UninstallPromptDialog(
            packageName = pendingUninstall.packageName,
            appLabel = pendingUninstall.appLabel,
            verdictLabel = pendingUninstall.verdict.label,
            onDismiss = viewModel::dismissUninstall,
            onConfirmUninstall = viewModel::confirmUninstall,
            onAnalyze = {
                viewModel.dismissUninstall()
                viewModel.rescanPackage(pendingUninstall.packageName)
            }
        )
    }
}

@Composable
private fun AvScannerTabContent(
    reports: List<AvScanReport>,
    searchQuery: String,
    selectedFilter: AvFilter,
    threatsCount: Int,
    riskCount: Int,
    adsAppsCount: Int,
    cleanCount: Int,
    onSearchChange: (String) -> Unit,
    onFilterSelect: (AvFilter) -> Unit,
    onOpenReport: (AvScanReport) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricMiniBox("Amenazas", threatsCount.toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            MetricMiniBox("Riesgo", riskCount.toString(), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            MetricMiniBox("Con Ads", adsAppsCount.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            MetricMiniBox("Limpias", cleanCount.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Filtrar resultados antivirus...") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("av_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AvFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.testTag("av_filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Pulsa «Analizar AV» para escanear todas las apps (con o sin perfil de trabajo).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("av_reports_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = reports, key = { it.packageName }) { report ->
                    AvReportCard(report = report, onClick = { onOpenReport(report) })
                }
            }
        }
    }
}

@Composable
private fun AvAdsAuditTabContent(
    reports: List<AvScanReport>,
    autoAnalyzeAds: Boolean,
    isScanning: Boolean,
    onToggleAutoAds: (Boolean) -> Unit,
    onRunAutoAds: () -> Unit,
    onOpenReport: (AvScanReport) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("av_ads_audit_list"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auditoría automática de Ads", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Inspecciona SDKs de publicidad (AdMob, AppLovin, Pangle, Mintegral, Airpush, Start.io, Meta Ads) en cada app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoAnalyzeAds,
                            onCheckedChange = onToggleAutoAds,
                            modifier = Modifier.testTag("av_auto_ads_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRunAutoAds,
                        enabled = !isScanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("av_run_auto_ads_button")
                    ) {
                        Icon(Icons.Rounded.AdsClick, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analizar Ads automáticamente ahora")
                    }
                }
            }
        }

        if (reports.isEmpty()) {
            item {
                TacticalNoticeBanner(
                    title = "Sin resultados de Ads todavía",
                    message = "Pulsa «Analizar Ads automáticamente ahora» para detectar redes publicitarias e indicadores de adware agresivo."
                )
            }
        } else {
            items(items = reports, key = { "ads_${it.packageName}" }) { report ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenReport(report) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconBadge(
                            packageName = report.packageName,
                            contentDescription = "Icono de ${report.appLabel}",
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.appLabel,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = report.adSdks.joinToString(", ") { it.name }.ifBlank { "Sin SDKs publicitarios" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusTag(
                            text = "${report.adSdks.size} Ads",
                            containerColor = if (report.adSdks.size >= 3) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (report.adSdks.size >= 3) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvEngineMenuTabContent(
    selectedDepth: AvScanDepth,
    batterySaverMode: Boolean,
    autoAnalyzeAds: Boolean,
    scanSystemApps: Boolean,
    useIncrementalCache: Boolean,
    databaseVersion: String,
    ruleCount: Int,
    onSelectDepth: (AvScanDepth) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit,
    onToggleAutoAds: (Boolean) -> Unit,
    onToggleSystemApps: (Boolean) -> Unit,
    onToggleIncrementalCache: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onForceDeepRescan: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("av_engine_menu_list"),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Nivel de detección del motor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nivel de detección del motor", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    AvScanDepth.entries.forEach { depth ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDepth(depth) }
                                .padding(vertical = 6.dp)
                                .testTag("av_depth_${depth.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDepth == depth,
                                onClick = { onSelectDepth(depth) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = depth.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selectedDepth == depth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = depth.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Menú de batería y automatización
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.BatterySaver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rendimiento, batería y automatización", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    EngineSwitchRow(
                        title = "Ahorro de batería (desactivar análisis automático)",
                        subtitle = "Desactiva el análisis automático al instalar o actualizar apps en segundo plano.",
                        checked = batterySaverMode,
                        onCheckedChange = onToggleBatterySaver,
                        testTag = "av_battery_saver_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    EngineSwitchRow(
                        title = "Analizar Ads automáticamente",
                        subtitle = "Audita SDKs publicitarios y adware dentro de cada escaneo del antivirus.",
                        checked = autoAnalyzeAds,
                        onCheckedChange = onToggleAutoAds,
                        testTag = "av_menu_auto_ads_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    EngineSwitchRow(
                        title = "Incluir apps del sistema en escaneo completo",
                        subtitle = "Analiza también paquetes preinstalados del fabricante (/system, /product).",
                        checked = scanSystemApps,
                        onCheckedChange = onToggleSystemApps,
                        testTag = "av_scan_system_switch"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    EngineSwitchRow(
                        title = "Caché incremental SHA-256 en Room",
                        subtitle = "Reutiliza veredictos si el APK no ha cambiado de versión para evitar tirones y ahorrar CPU.",
                        checked = useIncrementalCache,
                        onCheckedChange = onToggleIncrementalCache,
                        testTag = "av_incremental_cache_switch"
                    )
                }
            }
        }

        // Motores de código abierto y acciones de base
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Motores comunitarios integrados (100% local)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Base activa: $databaseVersion · $ruleCount firmas y reglas heurísticas.\n\n" +
                            "• Motor Aho-Corasick O(n): escaneo de bytecode DEX en una sola pasada sin bloqueos.\n" +
                            "• ClamAV-OSS Patterns: familias Trojan/Banker/Spyware/Adware/RAT.\n" +
                            "• YARA-Android & Koodous Heurística: carga dinámica DEX, su/Magisk, Accessibility+Overlay, exfiltración, payloads ocultos en assets/ y packers ELF UPX.\n" +
                            "• Exodus-Ads Definitions: detección de SDKs publicitarios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onForceDeepRescan,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("av_force_deep_rescan_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-escaneo total")
                        }
                        OutlinedButton(
                            onClick = onClearCache,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("av_clear_cache_button")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Limpiar caché")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun MetricMiniBox(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AvReportCard(
    report: AvScanReport,
    onClick: () -> Unit
) {
    val (bg, fg) = verdictColors(report.verdict)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("av_report_${report.packageName}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconBadge(
                packageName = report.packageName,
                contentDescription = "Icono de ${report.appLabel}",
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${report.packageName} · ${report.scanDepth.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusTag(text = report.verdict.label, containerColor = bg, contentColor = fg)
                    StatusTag(
                        text = "Score ${report.threatScore}/100",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (report.adSdks.isNotEmpty()) {
                        StatusTag(
                            text = "${report.adSdks.size} Ads",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun verdictColors(verdict: AvVerdict): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (verdict) {
        AvVerdict.MALWARE_DETECTED, AvVerdict.HIGH_RISK_MALWARE ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        AvVerdict.RISK ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        AvVerdict.ADWARE_INFO ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        AvVerdict.CLEAN, AvVerdict.NOT_SCANNED ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvReportDetailSheet(
    report: AvScanReport,
    isProfileOwner: Boolean,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onUninstall: () -> Unit,
    onIsolateWork: () -> Unit,
    onFreeze: () -> Unit,
    onOpenSettings: () -> Unit,
    onVerifySdk: (TrackerMatch) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val (bg, fg) = verdictColors(report.verdict)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconBadge(
                    packageName = report.packageName,
                    contentDescription = "Icono de ${report.appLabel}",
                    size = 52.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.appLabel, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${report.packageName} · v${report.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusTag(text = report.verdict.label, containerColor = bg, contentColor = fg)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Puntuación de riesgo: ${report.threatScore}/100 · Motor: ${report.scanDepth.label} (${report.scanDurationMs} ms)",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "SHA-256: ${report.sha256}\nInstalador: ${report.installerPackage ?: "Sideload / Desconocido"}\nFirma X.509: ${report.signerSummary}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUninstall,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("av_detail_uninstall_button")
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Desinstalar")
                }
                OutlinedButton(
                    onClick = onRescan,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("av_detail_rescan_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analizar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isProfileOwner) {
                    OutlinedButton(
                        onClick = onIsolateWork,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aislar en Trabajo")
                    }
                }
                OutlinedButton(
                    onClick = onFreeze,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AcUnit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Congelar app")
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("av_detail_settings_button")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes de aplicación", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Hallazgos del motor antivirus (${report.findings.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (report.findings.isEmpty()) {
                Text(
                    text = "Ninguna regla heurística ni firma de malware activada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                report.findings.forEach { finding ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${finding.engineName} · ${finding.ruleId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "+${finding.weight} pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(finding.title, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = finding.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "SDKs de Ads detectados (${report.adSdks.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (report.adSdks.isEmpty()) {
                Text(
                    text = "No se detectaron librerías publicitarias conocidas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                report.adSdks.forEach { sdk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sdk.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text(sdk.company, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = { onVerifySdk(sdk) },
                            modifier = Modifier.testTag("av_verify_sdk_${sdk.id}")
                        ) {
                            Icon(Icons.Rounded.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verificar rastreador")
                        }
                    }
                }
            }
        }
    }
}
