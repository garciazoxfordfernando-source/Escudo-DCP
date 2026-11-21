package com.example.data.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.example.domain.model.ShizukuState
import com.example.domain.model.ShizukuStatus
import com.example.service.EscudoShizukuUserService
import com.example.shizuku.IEscudoShizukuService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var userService: IEscudoShizukuService? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, EscudoShizukuUserService::class.java)
    )
        .daemon(false)
        .processNameSuffix("escudo_shizuku")
        .tag("escudo_shizuku_service")
        .version(1)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                val stub = IEscudoShizukuService.Stub.asInterface(service)
                userService = stub
                scope.launch {
                    val uid = runCatching { stub.uid }.getOrNull()
                    _state.value = ShizukuState(
                        status = ShizukuStatus.ACTIVE,
                        serviceBound = true,
                        privilegedUid = uid,
                        lastError = null
                    )
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            refreshStatus()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
        refreshStatus()
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindUserService()
            } else {
                refreshStatus()
            }
        }
    }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        }
        refreshStatus()
    }

    fun refreshStatus() {
        scope.launch {
            val installed = isShizukuAppInstalled()
            if (!installed) {
                _state.value = ShizukuState(status = ShizukuStatus.NOT_INSTALLED)
                return@launch
            }
            val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            if (!running) {
                _state.value = ShizukuState(status = ShizukuStatus.NOT_RUNNING)
                return@launch
            }
            val granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (!granted) {
                _state.value = ShizukuState(status = ShizukuStatus.NO_PERMISSION)
                return@launch
            }

            if (userService?.asBinder()?.pingBinder() == true) {
                val uid = runCatching { userService?.uid }.getOrNull()
                _state.value = ShizukuState(
                    status = ShizukuStatus.ACTIVE,
                    serviceBound = true,
                    privilegedUid = uid,
                    lastError = null
                )
            } else {
                _state.value = ShizukuState(
                    status = ShizukuStatus.ACTIVE,
                    serviceBound = false,
                    privilegedUid = null
                )
                bindUserService()
            }
        }
    }

    fun requestPermission() {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            } else {
                refreshStatus()
            }
        }
    }

    private fun bindUserService() {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            }
        }.onFailure { err ->
            _state.value = _state.value.copy(
                lastError = "No se pudo vincular UserService: ${err.localizedMessage}"
            )
        }
    }

    private fun isShizukuAppInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }

    suspend fun setPackageSuspended(packageName: String, suspended: Boolean): ActionResult =
        withService { service ->
            val ok = service.setPackageSuspended(packageName, suspended)
            if (ok) {
                ActionResult.Success(
                    if (suspended) "$packageName suspendida vía Shizuku."
                    else "$packageName reactivada vía Shizuku."
                )
            } else {
                ActionResult.Failure(service.lastError.ifBlank { "Shizuku no pudo suspender $packageName." })
            }
        }

    suspend fun setPackageEnabled(packageName: String, enabled: Boolean): ActionResult =
        withService { service ->
            val ok = service.setPackageEnabled(packageName, enabled)
            if (ok) {
                ActionResult.Success(
                    if (enabled) "$packageName habilitada vía Shizuku."
                    else "$packageName congelada (disable-user) vía Shizuku."
                )
            } else {
                ActionResult.Failure(service.lastError.ifBlank { "Shizuku no pudo cambiar estado de $packageName." })
            }
        }

    suspend fun setPackageHidden(packageName: String, hidden: Boolean): ActionResult =
        withService { service ->
            val ok = service.setPackageHidden(packageName, hidden)
            if (ok) {
                ActionResult.Success(
                    if (hidden) "$packageName ocultada vía Shizuku."
                    else "$packageName visible vía Shizuku."
                )
            } else {
                ActionResult.Failure(
                    service.lastError.ifBlank {
                        "pm hide requiere privilegios elevados o Perfil de Trabajo en esta versión de Android."
                    }
                )
            }
        }

    suspend fun setPermissionGranted(
        packageName: String,
        permission: String,
        granted: Boolean
    ): ActionResult = withService { service ->
        val ok = service.setPermissionGranted(packageName, permission, granted)
        if (ok) {
            ActionResult.Success(
                if (granted) "Permiso concedido vía Shizuku."
                else "Permiso revocado vía Shizuku."
            )
        } else {
            ActionResult.Failure(service.lastError.ifBlank { "No se pudo modificar el permiso vía Shizuku." })
        }
    }

    private suspend fun withService(block: (IEscudoShizukuService) -> ActionResult): ActionResult =
        withContext(Dispatchers.IO) {
            val service = userService
            if (service == null || !service.asBinder().pingBinder()) {
                refreshStatus()
                return@withContext ActionResult.Failure(
                    "UserService de Shizuku aún no está vinculado. Verifica el estado en Ajustes."
                )
            }
            runCatching { block(service) }.getOrElse { err ->
                ActionResult.Failure("Error IPC Shizuku: ${err.localizedMessage}")
            }
        }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 4201
    }
}
