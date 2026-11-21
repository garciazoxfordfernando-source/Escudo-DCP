package com.example.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ShizukuStatus
import com.example.ui.components.CapabilityMatrixCard
import com.example.ui.components.StatusTag
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(state.feedbackMessage) {
        val msg = state.feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    fun authenticateBeforeBiometricToggle(targetEnabled: Boolean) {
        if (activity == null) {
            viewModel.setBiometricLockEnabled(targetEnabled)
            return
        }
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuth = BiometricManager.from(context).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.setBiometricLockEnabled(targetEnabled)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.setBiometricLockEnabled(targetEnabled)
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirmar cambio de seguridad")
            .setSubtitle(
                if (targetEnabled) "Activar bloqueo biométrico al abrir Escudo"
                else "Desactivar bloqueo biométrico"
            )
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("settings_lazy_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Seguridad y Modos",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Cifrado local, biometría, integración opcional Shizuku y pruebas ADB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Seguridad propia: Biometría + DataStore cifrado
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Rounded.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Bloqueo biométrico al abrir", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Solicita huella, rostro o PIN del dispositivo antes de mostrar reglas y apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.securityConfig.biometricLockEnabled,
                            onCheckedChange = { authenticateBeforeBiometricToggle(it) },
                            modifier = Modifier.testTag("biometric_lock_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Rounded.EnhancedEncryption,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Almacenamiento cifrado", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "DataStore cifrado con AES-256-GCM en AndroidKeyStore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusTag(
                            text = if (state.securityConfig.encryptedStorageReady) "AES-GCM OK" else "LOCAL",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bloquear nuevas apps en Firewall", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "Las nuevas apps sincronizadas en Room nacen bloqueadas hasta aprobarlas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.securityConfig.firewallAutoBlockNewApps,
                            onCheckedChange = viewModel::setFirewallAutoBlockNewApps,
                            modifier = Modifier.testTag("auto_block_new_apps_switch")
                        )
                    }
                }
            }
        }

        // Modo Shizuku (Opcional)
        item {
            val shizuku = state.shizukuState
            val (badgeBg, badgeFg) = when (shizuku.status) {
                ShizukuStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                ShizukuStatus.NO_PERMISSION -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                ShizukuStatus.NOT_RUNNING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                ShizukuStatus.NOT_INSTALLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Shizuku (Opcional)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Habilita suspender, deshabilitar, ocultar y revocar permisos en el Perfil Principal mediante UserService.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = viewModel::refreshShizuku,
                            modifier = Modifier.testTag("refresh_shizuku_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Comprobar estado de Shizuku")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Estado actual", style = MaterialTheme.typography.labelLarge)
                            if (shizuku.privilegedUid != null) {
                                Text(
                                    text = "UserService vinculado · UID ${shizuku.privilegedUid}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (!shizuku.lastError.isNullOrBlank()) {
                                Text(
                                    text = shizuku.lastError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        StatusTag(
                            text = shizuku.status.label,
                            containerColor = badgeBg,
                            contentColor = badgeFg,
                            modifier = Modifier.testTag("shizuku_status_badge")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Usar Shizuku en Perfil Principal", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.securityConfig.shizukuModeEnabled,
                            onCheckedChange = viewModel::setShizukuModeEnabled,
                            modifier = Modifier.testTag("shizuku_mode_switch")
                        )
                    }

                    if (shizuku.status == ShizukuStatus.NO_PERMISSION) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = viewModel::requestShizukuPermission,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_shizuku_permission_button")
                        ) {
                            Text("Solicitar permiso a Shizuku")
                        }
                    } else if (shizuku.status != ShizukuStatus.ACTIVE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::refreshShizuku,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("retry_shizuku_button")
                        ) {
                            Text("Volver a comprobar servicio Shizuku")
                        }
                    }
                }
            }
        }

        // Guía rápida ADB para pruebas
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Comandos ADB de prueba y diagnóstico", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Verificar perfiles de usuario:\nadb shell pm list users\n\nEstablecer Profile Owner manual en un perfil existente (ej. user 10):\nadb shell dpm set-profile-owner --user 10 com.example/.service.EscudoDeviceAdminReceiver\n\nIniciar servicio Shizuku por ADB:\nadb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Matriz de capacidades
        item {
            CapabilityMatrixCard(rows = state.capabilityMatrix)
        }
    }
}
