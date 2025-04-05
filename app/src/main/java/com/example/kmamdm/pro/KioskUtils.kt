package com.example.kmamdm.pro

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.UserManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import com.example.kmamdm.extension.findActivity
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.utils.LegacyUtils
import com.example.kmamdm.utils.Utils
import okhttp3.internal.concat

object KioskUtils {
    private var mKioskApps: List<String> = listOf()
    private var mAdminComponentName: ComponentName? = null
    private var mDevicePolicyManager: DevicePolicyManager? = null

    fun isKioskModeRunning(context: Context): Boolean {
        return SettingsHelper.getInstance(context).isKioskModeRunning()
    }

    fun updateKioskAllowedApps(kioskApps: List<String>, activity: Activity) {
        // Nếu mKioskApps.size = 1 -> package đầu tiên là launcher
        // Nếu mKioskApps.size > 1 -> mdm app là launcher
        if (mKioskApps.size == 1) {
            // clear package persistent preferred activities
            setAsHomeApp(activity, false)
        }
        mKioskApps = kioskApps
        // set package persistent preferred activities
        setAsHomeApp(activity, true)

        val isAdmin = Utils.checkAdminMode(activity)
        setLockTask(activity, true, isAdmin)
    }

    // Start COSU kiosk mode
    fun startCosuKioskMode(kioskApps: List<String>, activity: Activity): Boolean {
        mKioskApps = kioskApps
        mAdminComponentName = LegacyUtils.getAdminComponentName(activity)
        mDevicePolicyManager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        SettingsHelper.getInstance(activity).setKioskModeRunning(true)
        setKioskPolicies(activity, true)
        Toast.makeText(activity, "Kiosk mode started with $kioskApps", Toast.LENGTH_SHORT).show()
        return true
    }

    fun unlockKiosk(activity: Activity) {
        mAdminComponentName = LegacyUtils.getAdminComponentName(activity)
        mDevicePolicyManager = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        SettingsHelper.getInstance(activity).setKioskModeRunning(false)
        setKioskPolicies(activity, false)
        Toast.makeText(activity, "Kiosk mode stopped", Toast.LENGTH_SHORT).show()
    }

    private fun setKioskPolicies(context: Context, enable: Boolean) {
        val isAdmin = Utils.checkAdminMode(context)
        if (isAdmin) {
            setRestrictions(enable)
            enableStayOnWhilePluggedIn(enable)
            setUpdatePolicy(enable)
            setAsHomeApp(context, enable)
            setKeyGuardEnabled(enable)
        }
        setLockTask(context, enable, isAdmin)
        setImmersiveMode(context, enable)
    }

    // region restrictions
    private fun setRestrictions(disallow: Boolean) {
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disallow)
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disallow)
        setUserRestriction(UserManager.DISALLOW_ADD_USER, disallow)
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, disallow)
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, disallow)
        mDevicePolicyManager!!.setStatusBarDisabled(mAdminComponentName!!, disallow)
    }

    private fun setUserRestriction(restriction: String, disallow: Boolean) = if (disallow) {
        mDevicePolicyManager!!.addUserRestriction(mAdminComponentName!!, restriction)
    } else {
        mDevicePolicyManager!!.clearUserRestriction(mAdminComponentName!!, restriction)
    }
    // endregion

    private fun enableStayOnWhilePluggedIn(active: Boolean) = if (active) {
        mDevicePolicyManager!!.setGlobalSetting(
            mAdminComponentName!!,
            Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
            (BatteryManager.BATTERY_PLUGGED_AC
                    or BatteryManager.BATTERY_PLUGGED_USB
                    or BatteryManager.BATTERY_PLUGGED_WIRELESS).toString()
        )
    } else {
        mDevicePolicyManager!!.setGlobalSetting(mAdminComponentName!!, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
    }

    private fun setLockTask(context: Context, start: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            var lockTaskPackages = arrayOf<String>()
            if (start) {
                lockTaskPackages = if (mKioskApps.size == 1) {
                    if (shouldSetAsHomeApp(context)) {
                        arrayOf(mKioskApps[0])
                    } else {
                        arrayOf(mKioskApps[0], context.packageName)
                    }
                } else {
                    // add the package name of the current app to the list of lock task packages for managing multiple apps
                    mKioskApps.toTypedArray().concat(context.packageName)
                }
            }
            mDevicePolicyManager!!.setLockTaskPackages(mAdminComponentName!!, lockTaskPackages)
        }
        context.findActivity()?.let {
            if (start) {
                it.startLockTask()
            } else {
                it.stopLockTask()
            }
        }
    }

    private fun setUpdatePolicy(enable: Boolean) {
        if (enable) {
            mDevicePolicyManager!!.setSystemUpdatePolicy(
                mAdminComponentName!!,
                SystemUpdatePolicy.createWindowedInstallPolicy(60, 120)
            )
        } else {
            mDevicePolicyManager!!.setSystemUpdatePolicy(mAdminComponentName!!, null)
        }
    }

    private fun setAsHomeApp(context: Context, enable: Boolean) {
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        if (enable && shouldSetAsHomeApp(context)) {
            val activityInfo = Utils.getLauncherByPackageName(context, mKioskApps.first())
            activityInfo?.let {
                val activity = ComponentName(it.packageName, it.name)
                mDevicePolicyManager!!.addPersistentPreferredActivity(mAdminComponentName, filter, activity)
            }
        } else {
            mDevicePolicyManager!!.clearPackagePersistentPreferredActivities(mAdminComponentName, mKioskApps.first())
            Utils.setDefaultLauncher(context)
        }
    }

    private fun shouldSetAsHomeApp(context: Context): Boolean {
        if (mKioskApps.size != 1) {
            return false
        }
        val filter = IntentFilter(Intent.ACTION_MAIN)
        filter.addCategory(Intent.CATEGORY_HOME)
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        val activityInfo = Utils.getLauncherByPackageName(context, mKioskApps.first())
        return activityInfo != null
    }

    private fun setKeyGuardEnabled(enable: Boolean) {
        mDevicePolicyManager!!.setKeyguardDisabled(mAdminComponentName!!, !enable)
    }

    @Suppress("DEPRECATION")
    private fun setImmersiveMode(context: Context, enable: Boolean) {
        val window = context.findActivity()?.window
        window?.let {
            if (enable) {
                val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                window.decorView.systemUiVisibility = flags
            } else {
                val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                window.decorView.systemUiVisibility = flags
            }
        }
    }
}