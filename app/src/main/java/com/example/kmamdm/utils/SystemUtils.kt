package com.example.kmamdm.utils

import android.annotation.TargetApi
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

object SystemUtils {
    private const val OP_SYSTEM_ALERT_WINDOW: Int = 24

    // https://stackoverflow.com/questions/10061154/how-to-programmatically-enable-disable-accessibility-service-in-android
    fun autoSetAccessibilityPermission(context: Context, packageName: String, className: String) {
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "$packageName/$className"
        )
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED, "1"
        )
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun autoSetOverlayPermission(context: Context, packageName: String): Boolean {
        return autoSetPermission(
            context,
            packageName,
            SystemUtils.OP_SYSTEM_ALERT_WINDOW,
            "Overlay"
        )
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun autoSetPermission(
        context: Context,
        packageName: String,
        permission: Int,
        permText: String
    ): Boolean {
        val packageManager = context.packageManager
        var uid = 0
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            uid = applicationInfo.uid
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return false
        }

        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

        // src/com/android/settings/applications/DrawOverlayDetails.java
        // See method: void setCanDrawOverlay(boolean newState)
        try {
            val clazz: Class<*> = AppOpsManager::class.java
            val method = clazz.getDeclaredMethod(
                "setMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            method.invoke(appOpsManager, permission, uid, packageName, AppOpsManager.MODE_ALLOWED)
            Log.d(Const.LOG_TAG, "$permText permission granted to $packageName")
            return true
        } catch (e: Exception) {
            Log.e(Const.LOG_TAG, Log.getStackTraceString(e))
            return false
        }
    }
}