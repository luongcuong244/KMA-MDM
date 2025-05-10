package com.example.kmamdm.utils

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.ui.screen.main.MainActivity


object Utils {
    // use this command to set the device owner     ->      adb shell dpm set-device-owner com.example.kmamdm/.AdminReceiver
    // use this command to clear the device owner   ->      adb shell dpm remove-active-admin com.example.kmamdm/.AdminReceiver
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

    fun factoryReset(context: Context): Boolean {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                dpm.wipeData(0)
            } else {
                dpm.wipeDevice(0)
            }
            return true
        } catch (e: java.lang.Exception) {
            return false
        }
    }

    fun canDrawOverlays(context: Context?): Boolean {
        return Settings.canDrawOverlays(context)
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

    fun setDefaultLauncher(context: Context) {
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        filter.addCategory(Intent.CATEGORY_DEFAULT)

        val activity = ComponentName(context, MainActivity::class.java)
        setPreferredActivity(context, filter, activity)
    }

    fun clearDefaultLauncher(context: Context) {
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        setPreferredActivity(context, filter, null)
    }

    private fun setPreferredActivity(
        context: Context,
        filter: IntentFilter,
        activity: ComponentName?
    ) {
        // Set the activity as the preferred option for the device.
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm =
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            if (activity != null) {
                clearAllLaunchers(context, dpm, adminComponentName)
                dpm.addPersistentPreferredActivity(adminComponentName, filter, activity)
            } else {
                dpm.clearPackagePersistentPreferredActivities(adminComponentName, context.packageName)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllLaunchers(context: Context, devicePolicyManager: DevicePolicyManager, adminComponentName: ComponentName) {
        val localPackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfoList = localPackageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            activityInfo?.let {
                devicePolicyManager.clearPackagePersistentPreferredActivities(adminComponentName, it.packageName)
            }
        }
    }

    fun getLauncherByPackageName(context: Context, packageName: String): ActivityInfo? {
        val localPackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfoList = localPackageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo
            if (activityInfo != null && activityInfo.packageName == packageName) {
                return activityInfo
            }
        }
        return null
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

    fun releaseUserRestrictions(context: Context, restrictions: String) {
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            return
        }

        val restrictionList =
            restrictions.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (r in restrictionList) {
            try {
                dpm.clearUserRestriction(adminComponentName, r.trim { it <= ' ' })
            } catch (_: java.lang.Exception) {
            }
        }
    }

    fun lockUserRestrictions(context: Context, restrictions: String) {
        val adminComponentName = LegacyUtils.getAdminComponentName(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            return
        }

        val restrictionList =
            restrictions.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (r in restrictionList) {
            try {
                dpm.addUserRestriction(adminComponentName, r.trim { it <= ' ' })
            } catch (_: java.lang.Exception) {
            }
        }
    }

    fun isMobileDataEnabled(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // A hack: use private API
        // https://stackoverflow.com/questions/12686899/test-if-background-data-and-packet-data-is-enabled-or-not?rq=1
        try {
            val clazz = Class.forName(cm.javaClass.name)
            val method = clazz.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true // Make the method callable
            // get the setting for "mobile data"
            return method.invoke(cm) as Boolean
        } catch (e: java.lang.Exception) {
            // Let it will be true by default
            return true
        }
    }

    fun autoGrantRequestedPermissions(
        context: Context, packageName: String,
        appPermissionStrategy: String? = null,
        forceSdCardPermissions: Boolean = false
    ): Boolean {
        var locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        var otherPermissionsState = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED

        /*// Determine the app permission strategy
        if (ServerConfig.APP_PERMISSIONS_ASK_LOCATION == appPermissionStrategy) {
            locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        } else if (ServerConfig.APP_PERMISSIONS_DENY_LOCATION == appPermissionStrategy) {
            locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        } else if (ServerConfig.APP_PERMISSIONS_ASK_ALL == appPermissionStrategy) {
            locationPermissionState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            if (packageName != context.packageName) {
                otherPermissionsState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            }
        }*/

        val devicePolicyManager = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)

        try {
            val permissions: MutableList<String> = getRuntimePermissions(
                    context.packageManager,
                    packageName
                )

            // Some devices do not include SD card permissions in the list of runtime permissions
            // So the files could not be read or written.
            // Here we add SD card permissions manually (device owner can grant them!)
            // This is done for the Headwind MDM launcher only
            if (forceSdCardPermissions) {
                var hasReadExtStorage = false
                var hasWriteExtStorage = false
                for (s in permissions) {
                    if (s == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        hasReadExtStorage = true
                    }
                    if (s == Manifest.permission.WRITE_EXTERNAL_STORAGE) {
                        hasWriteExtStorage = true
                    }
                }
                if (!hasReadExtStorage) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (!hasWriteExtStorage) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            for (permission in permissions) {
                val permissionState =
                    if (isLocationPermission(permission)) locationPermissionState else otherPermissionsState
                if (devicePolicyManager.getPermissionGrantState(
                        adminComponentName,
                        packageName, permission
                    ) != permissionState
                ) {
                    val success = devicePolicyManager.setPermissionGrantState(
                        adminComponentName,
                        packageName, permission, permissionState
                    )
                    if (!success) {
                        return false
                    }
                }
            }
        } catch (e: NoSuchMethodError) {
            // This exception is raised on Android 5.1
            e.printStackTrace()
            return false
        } catch (e: java.lang.Exception) {
            // No active admin ComponentInfo (not sure why could that happen)
            e.printStackTrace()
            return false
        }
        Log.i(Const.LOG_TAG, "Permissions automatically granted")
        return true
    }

    private fun isLocationPermission(permission: String): Boolean {
        return Manifest.permission.ACCESS_COARSE_LOCATION == permission ||
                Manifest.permission.ACCESS_FINE_LOCATION == permission ||
                Manifest.permission.ACCESS_BACKGROUND_LOCATION == permission
    }

    private fun getRuntimePermissions(
        packageManager: PackageManager,
        packageName: String
    ): MutableList<String> {
        val permissions: MutableList<String> = ArrayList()
        val packageInfo: PackageInfo?
        try {
            packageInfo =
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            return permissions
        }

        var manageStorage = false
        if (packageInfo?.requestedPermissions != null) {
            for (requestedPerm in packageInfo.requestedPermissions!!) {
                if (requestedPerm == Manifest.permission.MANAGE_EXTERNAL_STORAGE) {
                    manageStorage = true
                }
                if (isRuntimePermission(packageManager, requestedPerm)) {
                    permissions.add(requestedPerm)
                }
            }
            // There's a bug in Android 11+: MANAGE_EXTERNAL_STORAGE can't be automatically granted
            // but if Headwind MDM is granting WRITE_EXTERNAL_STORAGE, then the app can't request
            // MANAGE_EXTERNAL_STORAGE, it's locked!
            // So the workaround is do not request WRITE_EXTERNAL_STORAGE in this case
            if (manageStorage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                permissions.removeIf { s: String ->
                    (s == Manifest.permission.WRITE_EXTERNAL_STORAGE ||
                            s == Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
        return permissions
    }

    private fun isRuntimePermission(packageManager: PackageManager, permission: String): Boolean {
        try {
            val pInfo = packageManager.getPermissionInfo(permission, 0)
            if (pInfo != null) {
                if ((pInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
                    == PermissionInfo.PROTECTION_DANGEROUS
                ) {
                    return true
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
        }
        return false
    }
}