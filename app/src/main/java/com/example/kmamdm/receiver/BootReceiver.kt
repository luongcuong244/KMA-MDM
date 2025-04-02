package com.example.kmamdm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.startup.Initializer
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.utils.Const

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(Const.LOG_TAG, "Got the BOOT_RECEIVER broadcast")

        val settingsHelper: SettingsHelper = SettingsHelper.getInstance(context.applicationContext)

        val lastAppStartTime: Long = settingsHelper.getAppStartTime()
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        Log.d(
            Const.LOG_TAG,
            "appStartTime=$lastAppStartTime, bootTime=$bootTime"
        )
        if (lastAppStartTime < bootTime) {
            Log.i(
                Const.LOG_TAG,
                "KMA MDM wasn't started since boot, start initializing services"
            )
        } else {
            Log.i(Const.LOG_TAG, "KMA MDM is already started, ignoring BootReceiver")
            return
        }

        Initializer.init(context)
        Initializer.startServicesAndLoadConfig(context)

        SettingsHelper.getInstance(context).setMainActivityRunning(false)
        if (ProUtils.kioskModeRequired(context)) {
            Log.i(
                Const.LOG_TAG,
                "Kiosk mode required, forcing Headwind MDM to run in the foreground"
            )
            // If kiosk mode is required, then we just simulate clicking Home and starting MainActivity
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(homeIntent)
            return
        }
    }
}