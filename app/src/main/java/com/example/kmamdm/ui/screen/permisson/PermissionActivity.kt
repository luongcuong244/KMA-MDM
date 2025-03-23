package com.example.kmamdm.ui.screen.permisson

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.kmamdm.R
import com.example.kmamdm.ui.dialog.AdministratorModeDialog
import com.example.kmamdm.ui.dialog.ManageStorageDialog
import com.example.kmamdm.ui.dialog.OverlaySettingsDialog
import com.example.kmamdm.ui.dialog.UnknownSourcesDialog
import com.example.kmamdm.ui.screen.adminmoderequest.AdminModeRequestActivity
import com.example.kmamdm.ui.screen.main.MainActivity
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.Utils

class PermissionActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)
        checkAndStartLauncher()
    }

    private fun checkAndStartLauncher() {
        val unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1)
        if (unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences.edit().putInt(Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON)
                    .commit()
            } else {
                return
            }
        }

        val administratorMode = preferences.getInt(Const.PREFERENCES_ADMINISTRATOR, -1)
        if (administratorMode == -1) {
            if (checkAdminMode()) {
                preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON)
                    .commit()
            } else {
                return
            }
        }

        val overlayMode = preferences.getInt(Const.PREFERENCES_OVERLAY, -1)
        if (overlayMode == -1) {
            if (checkAlarmWindow()) {
                preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manageStorageMode = preferences.getInt(Const.PREFERENCES_MANAGE_STORAGE, -1)
            if (manageStorageMode == -1) {
                if (checkManageStorage()) {
                    preferences.edit()
                        .putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_ON).commit()
                } else {
                    return
                }
            }
        }

//        val accessibilityService = preferences.getInt(Const.PREFERENCES_ACCESSIBILITY_SERVICE, -1)
//        // Check the same condition as for usage stats here
//        // because accessibility is used as a secondary condition when usage stats is not available
//        if (ProUtils.isPro() && accessibilityService == -1 && needRequestUsageStats()) {
//            if (checkAccessibilityService()) {
//                preferences.edit
//                ().putInt
//                (Const.PREFERENCES_ACCESSIBILITY_SERVICE, Const.PREFERENCES_ON).commit
//                ()
//            } else {
//                createAndShowAccessibilityServiceDialog()
//                return
//            }
//        }

//        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig()
//                .getLockStatusBar() != null && settingsHelper.getConfig().getLockStatusBar()
//        ) {
//            // If the admin requested status bar lock (may be required for some early Samsung devices), block the status bar and right bar (App list) expansion
//            statusBarView = ProUtils.preventStatusBarExpansion(this)
//            rightToolbarView = ProUtils.preventApplicationsList(this)
//        }
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun checkUnknownSources(): Boolean {
        if (!Utils.canInstallPackages(this)) {
            val unknownSourcesDialog = UnknownSourcesDialog {
                continueUnknownSources()
            }
            unknownSourcesDialog.show(supportFragmentManager, "unknown_sources")
            return false
        } else {
            return true
        }
    }

    fun continueUnknownSources() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } else {
            // In Android Oreo and above, permission to install packages are set per each app
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse(
                        "package:$packageName"
                    )
                )
            )
        }
    }

    private fun checkAdminMode(): Boolean {
        if (!Utils.checkAdminMode(this)) {
            val administratorModeDialog = AdministratorModeDialog(
                onSkip = {
                    skipAdminMode()
                },
                onContinue = {
                    setAdminMode()
                }
            )
            administratorModeDialog.show(supportFragmentManager, "administrator_mode")
            return false
        }
        return true
    }

    private fun skipAdminMode() {
        preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    private fun setAdminMode() {
        startActivity(Intent(this, AdminModeRequestActivity::class.java))
    }

    private fun checkAlarmWindow(): Boolean {
        if (!Utils.canDrawOverlays(this)) {
            val overlaySettingsDialog = OverlaySettingsDialog(
                onSkip = {
                    overlayWithoutPermission()
                },
                onContinue = {
                    continueOverlay()
                }
            )
            overlaySettingsDialog.show(supportFragmentManager, "overlay_settings")
            return false
        } else {
            return true
        }
    }

    private fun overlayWithoutPermission() {
        preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    private fun continueOverlay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.overlays_not_supported, Toast.LENGTH_LONG).show()
            overlayWithoutPermission()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun checkManageStorage(): Boolean {
        if (!Environment.isExternalStorageManager()) {
            val manageStorageDialog = ManageStorageDialog(
                onSkip = {
                    storageWithoutPermission()
                },
                onContinue = {
                    continueStorage()
                }
            )
            manageStorageDialog.show(supportFragmentManager, "manage_storage")
            return false
        }
        return true
    }

    private fun storageWithoutPermission() {
        preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    private fun continueStorage() {
        try {
            val intent = Intent()
            intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts("package", this.packageName, null)
            intent.setData(uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            val intent = Intent()
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }
}