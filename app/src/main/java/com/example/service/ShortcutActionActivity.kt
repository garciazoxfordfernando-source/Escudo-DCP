package com.example.service

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.system.ActionResult
import com.example.data.system.ShizukuManager
import com.example.data.system.WorkProfileManager
import com.example.domain.model.ShizukuStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShortcutActionActivity : ComponentActivity() {

    @Inject
    lateinit var workProfileManager: WorkProfileManager

    @Inject
    lateinit var shizukuManager: ShizukuManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }

        lifecycleScope.launch {
            when (intent?.action) {
                ACTION_UNFREEZE_AND_LAUNCH -> {
                    val wpStatus = workProfileManager.getStatus()
                    if (wpStatus.isProfileOwner) {
                        workProfileManager.setAppHidden(packageName, false)
                        workProfileManager.setAppSuspended(packageName, false)
                        val launched = workProfileManager.launchWorkProfileApp(packageName)
                        if (launched is ActionResult.Failure) {
                            Toast.makeText(this@ShortcutActionActivity, launched.reason, Toast.LENGTH_LONG).show()
                        }
                    } else if (shizukuManager.state.value.status == ShizukuStatus.ACTIVE) {
                        shizukuManager.setPackageEnabled(packageName, true)
                        shizukuManager.setPackageSuspended(packageName, false)
                        val launched = workProfileManager.launchWorkProfileApp(packageName)
                        if (launched is ActionResult.Failure) {
                            Toast.makeText(this@ShortcutActionActivity, launched.reason, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        val launched = workProfileManager.launchWorkProfileApp(packageName)
                        if (launched is ActionResult.Failure) {
                            Toast.makeText(
                                this@ShortcutActionActivity,
                                "Ejecuta este acceso directo desde el Perfil de Trabajo o activa Shizuku.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            finish()
        }
    }

    companion object {
        const val ACTION_UNFREEZE_AND_LAUNCH = "com.example.escudo.shortcut.UNFREEZE_AND_LAUNCH"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }
}
