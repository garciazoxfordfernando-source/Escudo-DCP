package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppIconBadge
import com.example.ui.components.StatusTag
import com.example.ui.components.TacticalNoticeBanner
import com.example.ui.viewmodel.WorkProfileViewModel

@Composable
fun WorkProfileScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: WorkProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showWipeDialog by remember { mutableStateOf(false) }

    val provisionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refresh()
        } else {
            viewModel.refresh()
        }
    }

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("work_profile_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Perfil de Trabajo Aislado",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Contenedor DevicePolicyManager estilo Shelter",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = viewModel::refresh,
                    modifier = Modifier.testTag("refresh_work_profile_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar estado del perfil")
                }
            }
        }

        item {
            val status = state.status
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Work,
                            contentDescription = null,
                            tint = if (status.isProfileOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    status.isProfileOwner -> "Escudo es Profile Owner activo"
                                    status.hasWorkProfile -> "Perfil de Trabajo detectado (${status.workUserCount})"
                                    else -> "Sin Perfil de Trabajo configurado"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (status.isDeviceSupported) {
                                    "Soporte de sistema android.software.managed_users: Disponible"
                                } else {
                                    "Este dispositivo/ROM no declara soporte para perfiles administrados"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusTag(
                            text = if (status.isProfileOwner) "OWNER" else if (status.hasWorkProfile) "ACTIVO" else "INACTIVO",
                            containerColor = if (status.isProfileOwner) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (status.isProfileOwner) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!status.restrictionReason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TacticalNoticeBanner(
                            title = "Restricción del dispositivo o fabricante",
                            message = status.restrictionReason,
                            isWarning = true
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!status.hasWorkProfile && status.isProvisioningAllowed) {
                        Button(
                            onClick = {
                                runCatching {
                                    provisionLauncher.launch(viewModel.buildProvisioningIntent())
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_work_profile_button")
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Crear Perfil de Trabajo Escudo")
                        }
                    } else if (!status.hasWorkProfile) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    provisionLauncher.launch(viewModel.buildProvisioningIntent())
                                }
                            },
                            enabled = status.isDeviceSupported,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("attempt_provision_button")
                        ) {
                            Text("Intentar asistente de Perfil de Trabajo")
                        }
                    }

                    if (status.isProfileOwner) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.setAllWorkAppsSuspended(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("freeze_all_work_button")
                            ) {
                                Icon(Icons.Rounded.AcUnit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Congelar todo")
                            }
                            OutlinedButton(
                                onClick = { viewModel.setAllWorkAppsSuspended(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("unfreeze_all_work_button")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Descongelar")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showWipeDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wipe_work_profile_button")
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Eliminar Perfil de Trabajo y sus datos")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "¿Cómo funciona el aislamiento?",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OnboardingStep("1", "Android crea un usuario separado (ej. User 10) con almacenamiento, contactos y cuentas aisladas.")
                    OnboardingStep("2", "Al finalizar el asistente, abre el Escudo marcado con el maletín azul del Perfil de Trabajo.")
                    OnboardingStep("3", "Desde allí Escudo es Profile Owner: puede clonar APKs existentes sin descargas extra, congelarlas en segundo plano, ocultarlas y revocar permisos.")
                    OnboardingStep("4", "En Xiaomi/MIUI, ColorOS o dispositivos con Segundo Espacio activo, el fabricante puede bloquear ACTION_PROVISION_MANAGED_PROFILE; en ese caso usa ADB o Shizuku.")
                }
            }
        }

        if (state.status.isProfileOwner) {
            item {
                Text(
                    text = "Clonar apps del perfil principal (${state.primaryAppsToClone.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(
                items = state.primaryAppsToClone,
                key = { "clone_${it.packageName}" }
            ) { app ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconBadge(
                            packageName = app.packageName,
                            contentDescription = "Icono de ${app.label}",
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.cloneApp(app.packageName) },
                            modifier = Modifier.testTag("clone_action_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddCircleOutline,
                                contentDescription = "Clonar ${app.label} al perfil de trabajo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("¿Eliminar el Perfil de Trabajo?") },
            text = {
                Text("Se borrarán todas las aplicaciones clonadas y los datos almacenados dentro del perfil de trabajo Escudo Aislado. Tu perfil principal no se verá afectado.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeDialog = false
                        viewModel.wipeWorkProfile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_wipe_profile_button")
                ) {
                    Text("Eliminar perfil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun OnboardingStep(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(22.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
