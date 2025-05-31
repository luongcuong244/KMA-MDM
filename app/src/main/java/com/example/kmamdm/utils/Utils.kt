package com.example.kmamdm.utils

import android.Manifest
import android.annotation.SuppressLint
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
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.util.Log
import android.view.WindowManager
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.ui.screen.main.MainActivity
import java.util.UUID


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

    fun reboot(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponentName = LegacyUtils.getAdminComponentName(context)
            dpm.reboot(adminComponentName)
            return true
        } catch (e: java.lang.Exception) {
            return false
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

    fun lockUsbStorage(lock: Boolean, context: Context): Boolean {
        if (isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Deprecated way to lock USB
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    Settings.Secure.putInt(
                        context.contentResolver,
                        Settings.Secure.USB_MASS_STORAGE_ENABLED,
                        0
                    )
                } else {
                    Settings.Global.putInt(
                        context.contentResolver,
                        Settings.Global.USB_MASS_STORAGE_ENABLED,
                        0
                    )
                }
            } catch (e: java.lang.Exception) {
                return false
            }
            return true
        }

        val devicePolicyManager = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)

        try {
            if (lock) {
                devicePolicyManager.addUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_USB_FILE_TRANSFER
                )
                devicePolicyManager.addUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
                )
            } else {
                devicePolicyManager.clearUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_USB_FILE_TRANSFER
                )
                devicePolicyManager.clearUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA
                )
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun setScreenTimeoutPolicy(lock: Boolean?, timeout: Int?, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }

        val devicePolicyManager = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)

        try {
            if (lock == null || !lock) {
                // This means we should unlock screen timeout
                devicePolicyManager.clearUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT
                )
            } else {
                // Managed screen timeout
                devicePolicyManager.addUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_CONFIG_SCREEN_TIMEOUT
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && timeout != null) {
                    // This option is available in Android 9 and above
                    devicePolicyManager.setSystemSetting(
                        adminComponentName,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        "" + (timeout * 1000)
                    )
                }
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun lockVolume(lock: Boolean?, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }

        val devicePolicyManager = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)

        try {
            if (lock == null || !lock) {
                Log.d(Const.LOG_TAG, "Unlocking volume")
                devicePolicyManager.clearUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_ADJUST_VOLUME
                )
            } else {
                Log.d(Const.LOG_TAG, "Locking volume")
                devicePolicyManager.addUserRestriction(
                    adminComponentName,
                    UserManager.DISALLOW_ADJUST_VOLUME
                )
            }
        } catch (e: java.lang.Exception) {
            Log.w(Const.LOG_TAG, "Failed to lock/unlock volume: " + e.message)
            e.printStackTrace()
            return false
        }
        return true
    }

    fun setVolume(percent: Int, context: Context): Boolean {
        val streams = intArrayOf(
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM
        )
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            for (s in streams) {
                setVolumeInternal(audioManager, s, percent)

                var v = audioManager.getStreamVolume(s)
                if (v == 0) {
                    v = 1
                }
            }
            return true
        } catch (e: java.lang.Exception) {
            Log.w(Const.LOG_TAG, "Failed to set volume: " + e.message)
            e.printStackTrace()
            return false
        }
    }

    @Throws(java.lang.Exception::class)
    private fun setVolumeInternal(audioManager: AudioManager, stream: Int, percent: Int) {
        val maxVolume = audioManager.getStreamMaxVolume(stream)
        val volume = (maxVolume * percent) / 100
        audioManager.setStreamVolume(stream, volume, 0)
    }

    fun disableScreenshots(disabled: Boolean?, context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }

        val devicePolicyManager = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)

        try {
            devicePolicyManager.setScreenCaptureDisabled(adminComponentName, disabled ?: false)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun lockSafeBoot(context: Context): Boolean {
        if (!isDeviceOwner(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }

        val devicePolicyManager = context.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
        val adminComponentName = LegacyUtils.getAdminComponentName(context)

        try {
            devicePolicyManager.addUserRestriction(
                adminComponentName,
                UserManager.DISALLOW_SAFE_BOOT
            )
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun initPasswordReset(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val token: String = getDataToken(context)
                val dpm =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponentName = LegacyUtils.getAdminComponentName(context)
                if (dpm.setResetPasswordToken(adminComponentName, token.toByteArray())) {
                    if (!dpm.isResetPasswordTokenActive(adminComponentName)) {
                        Log.e(Const.LOG_TAG, "Password reset token will be activated once the user enters the current password next time.")
                    }
                } else {
                    Log.e(Const.LOG_TAG, "Failed to setup password reset token, password reset requests will fail")
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun passwordReset(context: Context, password: String?): Boolean {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val adminComponentName = LegacyUtils.getAdminComponentName(context)
                val tokenActive = dpm.isResetPasswordTokenActive(adminComponentName)
                if (!tokenActive) {
                    return false
                }
                return dpm.resetPasswordWithToken(
                    adminComponentName,
                    password,
                    getDataToken(context).toByteArray(),
                    0
                )
            } else {
                return dpm.resetPassword(password, 0)
            }
        } catch (e: java.lang.Exception) {
            return false
        }
    }

    private fun getDataToken(context: Context): String {
        var token = context.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
            .getString(Const.PREFERENCES_DATA_TOKEN, null)
        if (token == null) {
            token = UUID.randomUUID().toString()
            context.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putString(Const.PREFERENCES_DATA_TOKEN, token)
                .commit()
        }
        return token
    }

    fun overlayWindowType(): Int {
        // https://stackoverflow.com/questions/45867533/system-alert-window-permission-on-api-26-not-working-as-expected-permission-den
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ERROR
        }
    }
}