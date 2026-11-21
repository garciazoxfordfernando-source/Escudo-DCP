package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.domain.model.AppEntry
import com.example.domain.model.TrackerCategory
import com.example.ui.components.AppIconBadge
import com.example.ui.components.StatusTag
import com.example.ui.viewmodel.TrackerBlockRequest
import com.example.ui.viewmodel.TrackersViewModel

@Composable
fun TrackersScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: TrackersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnAfterConsent()
        }
    }

    LaunchedEffect(state.bannerMessage) {
        val msg = state.bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val scannedAppsCount = state.apps.count { it.trackersScanned }
    val appsWithTrackers = state.apps.count { it.trackers.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Escáner de Rastreadores",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Inspección local de classes.dex contra ${state.knownSignaturesCount} firmas de SDKs",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$scannedAppsCount / ${state.apps.size} APKs analizados",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$appsWithTrackers apps con SDKs · ${state.blockedPackages.size} bloqueadas en Firewall",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = viewModel::scanAllApps,
                        enabled = !state.isScanningBatch && state.apps.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("scan_all_trackers_button")
                    ) {
                        Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isScanningBatch) "Escaneando..." else "Escanear todo")
                    }

                    OutlinedButton(
                        onClick = viewModel::requestBlockAllDetectedTrackers,
                        enabled = !state.isScanningBatch && appsWithTrackers > 0,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (appsWithTrackers > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("block_all_trackers_button")
                    ) {
                        Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bloquear rastreadores", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (state.isScanningBatch && state.totalToScan > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.scannedCount.toFloat() / state.totalToScan.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Analizando ${state.scannedCount} de ${state.totalToScan} paquetes...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.onlyWithTrackers,
                onClick = viewModel::toggleOnlyWithTrackers,
                label = { Text("Solo con rastreadores") },
                modifier = Modifier.testTag("trackers_only_chip")
            )
            TrackerCategory.entries.forEach { category ->
                FilterChip(
                    selected = state.selectedCategory == category,
                    onClick = { viewModel.selectCategory(category) },
                    label = { Text(category.label) },
                    modifier = Modifier.testTag("tracker_cat_${category.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isLoadingApps) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("trackers_lazy_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = state.filteredApps,
                    key = { it.packageName }
                ) { app ->
                    val expanded = state.expandedPackage == app.packageName
                    val isBlocked = state.blockedPackages.contains(app.packageName)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            if (isBlocked) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleExpanded(app.packageName) }
                            .testTag("tracker_app_card_${app.packageName}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIconBadge(
                                    packageName = app.packageName,
                                    contentDescription = "Icono de ${app.label}",
                                    size = 40.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (app.isSystemApp) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            StatusTag(
                                                text = "Sistema",
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!app.trackersScanned) {
                                    OutlinedButton(
                                        onClick = { viewModel.scanSingleApp(app) },
                                        modifier = Modifier.testTag("scan_single_${app.packageName}")
                                    ) {
                                        Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Analizar")
                                    }
                                } else {
                                    if (isBlocked) {
                                        StatusTag(
                                            text = "Bloqueada",
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    StatusTag(
                                        text = if (app.trackers.isEmpty()) "0 SDK" else "${app.trackers.size} SDK",
                                        containerColor = if (app.trackers.isEmpty()) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        },
                                        contentColor = if (app.trackers.isEmpty()) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = if (expanded) "Contraer detalles" else "Expandir detalles"
                                    )
                                }
                            }

                            AnimatedVisibility(visible = expanded && app.trackersScanned) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (app.trackers.isEmpty()) {
                                        Text(
                                            text = "Sin coincidencias en la base local de SDKs.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        app.trackers.forEach { tracker ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = tracker.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "${tracker.company} · firma: ${tracker.matchedSignature}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    TextButton(
                                                        onClick = { viewModel.verifyTrackerInBrowser(app, tracker) },
                                                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                                        modifier = Modifier.testTag("verify_tracker_${app.packageName}_${tracker.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.TravelExplore,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Verificar rastreador")
                                                    }
                                                }
                                                StatusTag(
                                                    text = tracker.category.label,
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isBlocked) {
                                                OutlinedButton(
                                                    onClick = { viewModel.unblockAppTrackers(app) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("unblock_trackers_${app.packageName}")
                                                ) {
                                                    Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Permitir conexión")
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.requestBlockTrackers(app) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (app.isSystemApp) {
                                                            MaterialTheme.colorScheme.error
                                                        } else {
                                                            MaterialTheme.colorScheme.primary
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("block_trackers_${app.packageName}")
                                                ) {
                                                    Icon(
                                                        imageVector = if (app.isSystemApp) Icons.Rounded.WarningAmber else Icons.Rounded.Block,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Bloquear rastreadores")
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.openSystemAppSettings(app.packageName) },
                                                modifier = Modifier.testTag("tracker_open_settings_${app.packageName}")
                                            ) {
                                                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Ajustes")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pending = state.pendingBlockRequest
    if (pending != null) {
        TrackerBlockWarningDialog(
            request = pending,
            onDismiss = viewModel::dismissBlockConfirmation,
            onVerifyInBrowser = { targetApp ->
                viewModel.verifyAppTrackersInBrowser(targetApp)
            },
            onConfirm = { includeSystem ->
                viewModel.confirmBlock(includeSystemApps = includeSystem) { vpnIntent ->
                    vpnLauncher.launch(vpnIntent)
                }
            }
        )
    }
}

@Composable
private fun TrackerBlockWarningDialog(
    request: TrackerBlockRequest,
    onDismiss: () -> Unit,
    onVerifyInBrowser: (AppEntry) -> Unit,
    onConfirm: (includeSystemApps: Boolean) -> Unit
) {
    when (request) {
        is TrackerBlockRequest.SingleApp -> {
            val app = request.app
            if (app.isSystemApp) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    title = { Text("Advertencia: Aplicación del sistema") },
                    text = {
                        Text(
                            "«${app.label}» (${app.packageName}) es una aplicación del sistema.\n\n" +
                                "Bloquear un rastreador o cortar el tráfico de red en una app del sistema podría poner en riesgo el funcionamiento de tu celular (notificaciones, servicios de sistema, llamadas o estabilidad).\n\n" +
                                "Pulsa «Verificar rastreador» para buscar en el navegador por qué está este rastreador antes de continuar bajo tu responsabilidad."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { onConfirm(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("confirm_block_system_tracker")
                        ) {
                            Text("Bloquear bajo mi riesgo")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { onVerifyInBrowser(app) },
                                modifier = Modifier.testTag("dialog_verify_tracker_${app.packageName}")
                            ) {
                                Text("Verificar rastreador")
                            }
                            TextButton(onClick = onDismiss) {
                                Text("Cancelar")
                            }
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    },
                    title = { Text("Bloquear rastreadores") },
                    text = {
                        Text(
                            "«${app.label}» es una aplicación de usuario.\n\n" +
                                "Puedes continuar sin riesgo para el sistema operativo, pero con algunas consecuencias: al bloquear su conectividad en el Firewall local para frenar los rastreadores, las funciones en línea, inicio de sesión o avisos de esta aplicación dejarán de funcionar."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { onConfirm(false) },
                            modifier = Modifier.testTag("confirm_block_user_tracker")
                        ) {
                            Text("Bloquear rastreadores")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { onVerifyInBrowser(app) },
                                modifier = Modifier.testTag("dialog_verify_tracker_${app.packageName}")
                            ) {
                                Text("Verificar rastreador")
                            }
                            TextButton(onClick = onDismiss) {
                                Text("Cancelar")
                            }
                        }
                    }
                )
            }
        }

        is TrackerBlockRequest.BatchApps -> {
            val hasSystem = request.systemApps.isNotEmpty()
            val sampleApp = request.systemApps.firstOrNull() ?: request.userApps.firstOrNull()
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = if (hasSystem) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                        contentDescription = null,
                        tint = if (hasSystem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        if (hasSystem) "Advertencia: Incluye apps del sistema"
                        else "Bloquear rastreadores en lote"
                    )
                },
                text = {
                    Text(
                        if (hasSystem) {
                            "Se detectaron rastreadores en ${request.userApps.size} app(s) de usuario y ${request.systemApps.size} app(s) del sistema.\n\n" +
                                "• Apps del sistema: bloquearlas puede poner en riesgo el celular (servicios esenciales, red o notificaciones). Verifica en la web por qué tienen rastreadores.\n" +
                                "• Apps de usuario: puedes bloquearlas sin riesgo para Android, sabiendo que sus funciones en línea se detendrán."
                        } else {
                            "Se bloquearán ${request.userApps.size} app(s) de usuario en el Firewall local.\n\n" +
                                "Puedes continuar sin riesgo para el sistema, pero con la consecuencia de que las funciones en línea de esas apps dejarán de conectar mientras estén bloqueadas."
                        }
                    )
                },
                confirmButton = {
                    if (hasSystem && request.userApps.isNotEmpty()) {
                        Button(
                            onClick = { onConfirm(false) },
                            modifier = Modifier.testTag("confirm_block_batch_user_only")
                        ) {
                            Text("Solo apps de usuario (${request.userApps.size})")
                        }
                    } else {
                        Button(
                            onClick = { onConfirm(hasSystem) },
                            colors = if (hasSystem) {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier.testTag("confirm_block_batch_all")
                        ) {
                            Text(if (hasSystem) "Bloquear bajo mi riesgo" else "Bloquear todas")
                        }
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (sampleApp != null) {
                            TextButton(onClick = { onVerifyInBrowser(sampleApp) }) {
                                Text("Verificar")
                            }
                        }
                        if (hasSystem && request.userApps.isNotEmpty()) {
                            TextButton(
                                onClick = { onConfirm(true) },
                                modifier = Modifier.testTag("confirm_block_batch_include_system")
                            ) {
                                Text("Incluir sistema", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar")
                        }
                    }
                }
            )
        }
    }
}
