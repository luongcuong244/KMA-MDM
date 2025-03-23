package com.example.kmamdm.utils

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException

object Utils {
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Global setting works for Android 7 and below
            try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.INSTALL_NON_MARKET_APPS
                ) == 1
            } catch (e: SettingNotFoundException) {
                true
            }
        } else {
            context.packageManager.canRequestPackageInstalls()
        }
    }

    fun checkAdminMode(context: Context): Boolean {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponentName = LegacyUtils.getAdminComponentName(context)
            val isAdminActive = dpm.isAdminActive(adminComponentName)
            return isAdminActive
        } catch (e: Exception) {
            return true
        }
    }

    fun canDrawOverlays(context: Context?): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(context)
    }
}