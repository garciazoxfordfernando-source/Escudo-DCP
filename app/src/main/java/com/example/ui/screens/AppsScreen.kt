package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.domain.model.AppEntry
import com.example.domain.model.ProfileScope
import com.example.domain.model.ShizukuStatus
import com.example.domain.model.TrackerMatch
import com.example.ui.components.AppIconBadge
import com.example.ui.components.RiskBadge
import com.example.ui.components.StatusTag
import com.example.ui.components.TacticalNoticeBanner
import com.example.ui.components.UninstallPromptDialog
import com.example.ui.viewmodel.AppFilter
import com.example.ui.viewmodel.AppsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    snackbarHostState: SnackbarHostState,
    onNavigateToWorkProfile: () -> Unit,
    viewModel: AppsViewModel = hiltViewModel()
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

    val shizukuReady = state.shizukuModeEnabled && state.shizukuState.status == ShizukuStatus.ACTIVE

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
                    text = "Escudo de Aplicaciones",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${state.filteredApps.size} apps visibles · ${state.selectedScope.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = viewModel::refreshApps,
                modifier = Modifier.testTag("refresh_apps_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Actualizar lista de aplicaciones"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_scope_switcher")
        ) {
            SegmentedButton(
                selected = state.selectedScope == ProfileScope.PRIMARY,
                onClick = { viewModel.selectScope(ProfileScope.PRIMARY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.testTag("scope_primary_tab")
            ) {
                Text("Perfil Principal")
            }
            SegmentedButton(
                selected = state.selectedScope == ProfileScope.WORK,
                onClick = { viewModel.selectScope(ProfileScope.WORK) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.testTag("scope_work_tab")
            ) {
                Text("Perfil de Trabajo")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            state.selectedScope == ProfileScope.PRIMARY && !shizukuReady -> {
                TacticalNoticeBanner(
                    title = "Modo solo lectura en Perfil Principal",
                    message = "Android prohíbe congelar, ocultar o revocar permisos de otras apps en el perfil principal sin ser Profile Owner. Puedes inspeccionar permisos/rastreadores, abrir Ajustes del sistema, aislar en el Perfil de Trabajo o activar Shizuku."
                )
            }
            state.selectedScope == ProfileScope.PRIMARY && shizukuReady -> {
                TacticalNoticeBanner(
                    title = "Modo Shizuku activo en Perfil Principal",
                    message = "Ejecutando pm suspend, disable-user y revoke/grant vía UserService sin bloquear el hilo principal.",
                    isWarning = false
                )
            }
            state.selectedScope == ProfileScope.WORK && !state.workProfileStatus.isProfileOwner -> {
                TacticalNoticeBanner(
                    title = "Perfil de Trabajo no administrado desde esta instancia",
                    message = if (state.workProfileStatus.hasWorkProfile) {
                        "Abre Escudo dentro del cajón del Perfil de Trabajo para congelar, ocultar y gestionar permisos como Profile Owner."
                    } else {
                        "Aún no has creado un Perfil de Trabajo aislado. Configúralo en la pestaña Perfil."
                    },
                    isWarning = true
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::updateSearchQuery,
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
            },
            placeholder = { Text("Buscar app o paquete...") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apps_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.selectedFilter == filter,
                    onClick = { viewModel.selectFilter(filter) },
                    label = { Text(filter.label) },
                    modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Work,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (state.selectedScope == ProfileScope.WORK && !state.workProfileStatus.hasWorkProfile) {
                            "No hay un Perfil de Trabajo activo todavía."
                        } else {
                            "Ninguna app coincide con el filtro actual."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.selectedScope == ProfileScope.WORK && !state.workProfileStatus.hasWorkProfile) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToWorkProfile,
                            modifier = Modifier.testTag("go_to_work_wizard_button")
                        ) {
                            Text("Configurar Perfil de Trabajo")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("apps_lazy_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = state.filteredApps,
                    key = { "${it.profileScope.name}_${it.packageName}" }
                ) { app ->
                    AppCardItem(
                        app = app,
                        canControlInScope = (app.profileScope == ProfileScope.WORK && state.workProfileStatus.isProfileOwner) ||
                            (app.profileScope == ProfileScope.PRIMARY && shizukuReady),
                        onClick = { viewModel.openAppDetail(app) },
                        onToggleFreeze = { viewModel.toggleSuspended(app) }
                    )
                }
            }
        }
    }

    val selected = state.selectedApp
    if (selected != null) {
        AppDetailSheet(
            app = selected,
            isProfileOwner = state.workProfileStatus.isProfileOwner,
            shizukuActive = shizukuReady,
            isScanning = state.scanningAppPackage == selected.packageName,
            isTrackersBlocked = state.blockedPackages.contains(selected.packageName),
            onDismiss = viewModel::dismissAppDetail,
            onCloneToWork = { viewModel.cloneToWorkProfile(selected.packageName) },
            onToggleSuspend = { viewModel.toggleSuspended(selected) },
            onToggleDisableShizuku = { viewModel.toggleEnabledViaShizuku(selected) },
            onToggleHide = { viewModel.toggleHidden(selected) },
            onTogglePermission = { perm -> viewModel.togglePermission(selected, perm) },
            onScanTrackers = { viewModel.scanAppTrackers(selected) },
            onVerifyTracker = { tracker -> viewModel.verifyTrackerInBrowser(selected, tracker) },
            onRequestBlockTrackers = { viewModel.requestBlockTrackers(selected) },
            onUnblockTrackers = { viewModel.unblockTrackers(selected) },
            onRequestUninstall = { viewModel.requestUninstall(selected) },
            onCreateShortcut = { viewModel.createShortcut(selected) },
            onLaunchWorkApp = { viewModel.launchWorkApp(selected.packageName) },
            onOpenSystemSettings = { viewModel.openSystemSettings(selected.packageName) }
        )
    }

    val pendingUninstallApp = state.pendingUninstallApp
    if (pendingUninstallApp != null) {
        UninstallPromptDialog(
            packageName = pendingUninstallApp.packageName,
            appLabel = pendingUninstallApp.label,
            verdictLabel = "Riesgo ${pendingUninstallApp.riskLevel.label}",
            onDismiss = viewModel::dismissUninstall,
            onConfirmUninstall = viewModel::confirmUninstall,
            onAnalyze = {
                viewModel.dismissUninstall()
                viewModel.scanAppTrackers(pendingUninstallApp)
            }
        )
    }

    val pendingBlockApp = state.pendingTrackerBlockApp
    if (pendingBlockApp != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBlockTrackersDialog,
            icon = {
                Icon(
                    imageVector = if (pendingBlockApp.isSystemApp) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                    contentDescription = null,
                    tint = if (pendingBlockApp.isSystemApp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    if (pendingBlockApp.isSystemApp) "Advertencia: Aplicación del sistema"
                    else "Bloquear rastreadores"
                )
            },
            text = {
                Text(
                    if (pendingBlockApp.isSystemApp) {
                        "«${pendingBlockApp.label}» es una aplicación del sistema.\n\n" +
                            "Bloquear un rastreador o la red de una app del sistema puede poner en riesgo tu celular (notificaciones, servicios o estabilidad). Pulsa «Verificar rastreador» para buscar en el navegador por qué está el rastreador antes de continuar."
                    } else {
                        "«${pendingBlockApp.label}» es una aplicación de usuario.\n\n" +
                            "Puedes continuar sin riesgo para el sistema operativo, pero con algunas consecuencias: las funciones online de esta app dejarán de conectar mientras esté bloqueada."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmBlockTrackers { vpnIntent ->
                            vpnLauncher.launch(vpnIntent)
                        }
                    },
                    colors = if (pendingBlockApp.isSystemApp) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.testTag("detail_confirm_block_trackers")
                ) {
                    Text(if (pendingBlockApp.isSystemApp) "Bloquear bajo mi riesgo" else "Bloquear rastreadores")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = { viewModel.verifyAppTrackersInBrowser(pendingBlockApp) },
                        modifier = Modifier.testTag("detail_dialog_verify_tracker")
                    ) {
                        Text("Verificar rastreador")
                    }
                    TextButton(onClick = viewModel::dismissBlockTrackersDialog) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

@Composable
private fun AppCardItem(
    app: AppEntry,
    canControlInScope: Boolean,
    onClick: () -> Unit,
    onToggleFreeze: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("app_card_${app.packageName}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconBadge(
                packageName = app.packageName,
                contentDescription = "Icono de ${app.label}"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RiskBadge(riskLevel = app.riskLevel)
                    StatusTag(
                        text = "${app.grantedDangerousCount}/${app.dangerousPermissions.size} perm.",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.isSuspended || !app.isEnabled) {
                        StatusTag(
                            text = "Congelada",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    if (app.isInstalledInWorkProfile && app.profileScope == ProfileScope.PRIMARY) {
                        StatusTag(
                            text = "En Trabajo",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (app.trackers.isNotEmpty()) {
                        StatusTag(
                            text = "${app.trackers.size} SDK",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            if (canControlInScope) {
                IconButton(
                    onClick = onToggleFreeze,
                    modifier = Modifier.testTag("quick_freeze_${app.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AcUnit,
                        contentDescription = if (app.isSuspended) "Descongelar ${app.label}" else "Congelar ${app.label}",
                        tint = if (app.isSuspended) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(
    app: AppEntry,
    isProfileOwner: Boolean,
    shizukuActive: Boolean,
    isScanning: Boolean,
    isTrackersBlocked: Boolean,
    onDismiss: () -> Unit,
    onCloneToWork: () -> Unit,
    onToggleSuspend: () -> Unit,
    onToggleDisableShizuku: () -> Unit,
    onToggleHide: () -> Unit,
    onTogglePermission: (com.example.domain.model.PermissionDetail) -> Unit,
    onScanTrackers: () -> Unit,
    onVerifyTracker: (TrackerMatch) -> Unit,
    onRequestBlockTrackers: () -> Unit,
    onUnblockTrackers: () -> Unit,
    onRequestUninstall: () -> Unit,
    onCreateShortcut: () -> Unit,
    onLaunchWorkApp: () -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    packageName = app.packageName,
                    contentDescription = "Icono de ${app.label}",
                    size = 54.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${app.packageName} · v${app.versionName} · UID ${app.uid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RiskBadge(riskLevel = app.riskLevel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            val canManageWork = app.profileScope == ProfileScope.WORK && isProfileOwner
            val canManageShizuku = app.profileScope == ProfileScope.PRIMARY && shizukuActive

            Text(
                text = "Acciones de aislamiento",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (canManageWork || canManageShizuku) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onToggleSuspend,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_suspend_button")
                    ) {
                        Icon(Icons.Rounded.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (app.isSuspended) "Descongelar" else "Congelar")
                    }
                    OutlinedButton(
                        onClick = onToggleHide,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_hide_button")
                    ) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (app.isHidden) "Mostrar" else "Ocultar")
                    }
                }
                if (canManageShizuku) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onToggleDisableShizuku,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_disable_shizuku_button")
                    ) {
                        Text(
                            if (app.isEnabled) "Deshabilitar (pm disable-user) vía Shizuku"
                            else "Habilitar (pm enable) vía Shizuku"
                        )
                    }
                }
            } else {
                TacticalNoticeBanner(
                    title = "Controles restringidos en Perfil Principal",
                    message = "Sin Profile Owner ni Shizuku no es posible congelar, ocultar o revocar permisos directamente. Clona la app en el Perfil de Trabajo o abre Ajustes del sistema.",
                    isWarning = false
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isProfileOwner) {
                    OutlinedButton(
                        onClick = onCloneToWork,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_clone_button")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clonar en Trabajo")
                    }
                }
                OutlinedButton(
                    onClick = onOpenSystemSettings,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_open_settings_button")
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajustes Android")
                }
            }

            if (!app.isSystemApp) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestUninstall,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_uninstall_button")
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Desinstalar aplicación")
                }
            }

            if (canManageWork || canManageShizuku) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCreateShortcut,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_shortcut_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Acceso directo")
                    }
                    OutlinedButton(
                        onClick = onLaunchWorkApp,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("detail_launch_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lanzar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SDKs de rastreo (${app.trackers.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(
                    onClick = onScanTrackers,
                    enabled = !isScanning,
                    modifier = Modifier.testTag("detail_scan_trackers_button")
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (app.trackersScanned) "Re-escanear APK" else "Analizar APK")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            if (!app.trackersScanned) {
                Text(
                    text = "Pulsa «Analizar APK» para buscar firmas conocidas en classes.dex.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (app.trackers.isEmpty()) {
                Text(
                    text = "No se detectaron SDKs de rastreo de la base local.",
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
                                text = "${tracker.company} · ${tracker.matchedSignature}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { onVerifyTracker(tracker) },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("detail_verify_tracker_${tracker.id}")
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
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (isTrackersBlocked) {
                    OutlinedButton(
                        onClick = onUnblockTrackers,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_unblock_trackers_button")
                    ) {
                        Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Permitir conexión (Rastreadores bloqueados)")
                    }
                } else {
                    Button(
                        onClick = onRequestBlockTrackers,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (app.isSystemApp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_block_trackers_button")
                    ) {
                        Icon(
                            imageVector = if (app.isSystemApp) Icons.Rounded.WarningAmber else Icons.Rounded.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bloquear rastreadores")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Permisos peligrosos (${app.grantedDangerousCount} concedidos de ${app.dangerousPermissions.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (app.dangerousPermissions.isEmpty()) {
                Text(
                    text = "Esta aplicación no solicita permisos peligrosos en tiempo de ejecución.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                app.dangerousPermissions.forEach { perm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = perm.shortName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = perm.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (perm.canToggleInCurrentScope) {
                            Switch(
                                checked = perm.isGranted,
                                onCheckedChange = { onTogglePermission(perm) },
                                modifier = Modifier.testTag("perm_switch_${perm.shortName}")
                            )
                        } else {
                            IconButton(
                                onClick = onOpenSystemSettings,
                                modifier = Modifier.testTag("perm_settings_${perm.shortName}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Abrir ajustes para ${perm.shortName}",
                                    tint = if (perm.isGranted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
