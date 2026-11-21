package com.example.service

import android.content.Context
import android.os.Process
import com.example.shizuku.IEscudoShizukuService
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Se ejecuta en un proceso separado bajo privilegios de Shizuku (shell UID 2000 o root UID 0).
 * Nunca se invoca desde el hilo principal de la app cliente.
 */
class EscudoShizukuUserService : IEscudoShizukuService.Stub {

    @Suppress("unused")
    private var context: Context? = null

    @Volatile
    private var lastErrorMessage: String = ""

    constructor(context: Context?) {
        this.context = context
    }

    constructor() : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int = Process.myUid()

    override fun setPackageSuspended(packageName: String, suspended: Boolean): Boolean {
        val action = if (suspended) "suspend" else "unsuspend"
        return runPmCommand("pm", action, packageName)
    }

    override fun setPackageEnabled(packageName: String, enabled: Boolean): Boolean {
        return if (enabled) {
            runPmCommand("pm", "enable", packageName)
        } else {
            runPmCommand("pm", "disable-user", "--user", "0", packageName)
        }
    }

    override fun setPackageHidden(packageName: String, hidden: Boolean): Boolean {
        val action = if (hidden) "hide" else "unhide"
        return runPmCommand("pm", action, packageName)
    }

    override fun setPermissionGranted(
        packageName: String,
        permission: String,
        granted: Boolean
    ): Boolean {
        val action = if (granted) "grant" else "revoke"
        return runPmCommand("pm", action, packageName, permission)
    }

    override fun getLastError(): String = lastErrorMessage

    private fun runPmCommand(vararg command: String): Boolean {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }.trim()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                lastErrorMessage = ""
                true
            } else {
                lastErrorMessage = output.ifBlank { "Código de salida $exitCode" }
                false
            }
        } catch (t: Throwable) {
            lastErrorMessage = t.localizedMessage ?: t.javaClass.simpleName
            false
        }
    }
}
