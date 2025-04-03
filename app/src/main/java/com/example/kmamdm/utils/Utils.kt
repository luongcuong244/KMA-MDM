package com.example.kmamdm.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.ui.screen.main.MainActivity

object Utils {
    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun setDefaultLauncher(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        filter.addCategory(Intent.CATEGORY_DEFAULT)

        val activity = ComponentName(context, MainActivity::class.java)
        setPreferredActivity(context, filter, activity)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun clearDefaultLauncher(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        setPreferredActivity(context, filter, null)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setPreferredActivity(
        context: Context,
        filter: IntentFilter,
        activity: ComponentName?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }
        // Set the activity as the preferred option for the device.
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            if (activity != null) {
                dpm.addPersistentPreferredActivity(adminComponentName, filter, activity)
            } else {
                dpm.clearPackagePersistentPreferredActivities(
                    adminComponentName,
                    context.packageName
                )
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun getDefaultLauncher(context: Context): String? {
        val defaultLauncherInfo = getDefaultLauncherInfo(context)
        return defaultLauncherInfo?.packageName
    }

    private fun getDefaultLauncherInfo(context: Context): ActivityInfo? {
        val localPackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val info = localPackageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (info?.activityInfo == null) {
            return null
        }
        return info.activityInfo
    }
}