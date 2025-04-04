package com.example.kmamdm.pro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.ui.screen.main.MainActivity
import com.example.kmamdm.utils.Utils

object KioskUtils {
    fun preventStatusBarExpansion(activity: Activity?): View? {
        // Stub
        return null
    }

    fun createKioskUnlockButton(activity: Activity?): View? {
        // Stub
        return null
    }

    fun isKioskAppInstalled(context: Context?): Boolean {
        // Stub
        return false
    }

    fun isKioskModeRunning(context: Context): Boolean {
        return SettingsHelper.getInstance(context).isKioskModeRunning()
    }

    fun getKioskAppIntent(kioskApp: String, activity: Activity): Intent? {
        return activity.packageManager.getLaunchIntentForPackage(kioskApp)
    }

    // Start COSU kiosk mode
    fun startCosuKioskMode(kioskApp: String, activity: Activity): Boolean {
        SettingsHelper.getInstance(activity).setKioskModeRunning(true)
        setKioskPolicies(activity, true)
        return true
    }

    // Update app list in the kiosk mode
    fun updateKioskAllowedApps(kioskApp: String?, activity: Activity?, enableSettings: Boolean) {
        // Stub
    }

    fun unlockKiosk(activity: Activity) {
        SettingsHelper.getInstance(activity).setKioskModeRunning(false)
        setKioskPolicies(activity, false)
    }

    private fun setKioskPolicies(context: Context, enable: Boolean) {
        if (Utils.isDeviceOwner(context)) {
            setRestrictions(enable)
            enableStayOnWhilePluggedIn(enable)
            setUpdatePolicy(enable)
            setAsHomeApp(enable)
            setKeyGuardEnabled(enable)
        }
        setLockTask(enable, isAdmin)
        setImmersiveMode(enable)
    }
}