package com.example.kmamdm.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import com.example.kmamdm.model.ServerConfig

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

    @SuppressLint("SourceLockedOrientationActivity")
    fun setOrientation(activity: Activity, config: ServerConfig) {
        var loggedOrientation = "unspecified"
        if (config.orientation !== 0) {
            when (config.orientation) {
                Const.SCREEN_ORIENTATION_PORTRAIT -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    loggedOrientation = "portrait"
                }

                Const.SCREEN_ORIENTATION_LANDSCAPE -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    loggedOrientation = "landscape"
                }

                else -> activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        Log.i(Const.LOG_TAG, "Set orientation: $loggedOrientation")
    }
}