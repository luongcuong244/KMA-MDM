package com.example.kmamdm.ui.adapter

import android.content.Context
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.ApplicationConfig
import com.example.kmamdm.utils.AppInfo
import java.util.Collections

class AppShortcutManager {
    fun getInstalledAppCount(context: Context, bottom: Boolean): Int {
        val requiredPackages = mutableMapOf<String, ApplicationConfig>()
        getConfiguredApps(context, requiredPackages)
        val packs = context.packageManager.getInstalledApplications(0)
        // Calculate applications
        var packageCount = 0
        for (i in packs.indices) {
            val p = packs[i]
            if (context.packageManager.getLaunchIntentForPackage(p.packageName) != null &&
                requiredPackages.containsKey(p.packageName)
            ) {
                packageCount++
            }
        }
        return packageCount
    }

    fun getInstalledApps(context: Context, bottom: Boolean): List<AppInfo> {
        val requiredPackages = mutableMapOf<String, ApplicationConfig>()
        getConfiguredApps(context, requiredPackages)

        val appInfos = mutableListOf<AppInfo>()
        val packs = context.packageManager.getInstalledApplications(0)
        // First we display app icons
        for (i in packs.indices) {
            val p = packs[i]
            if (!requiredPackages.containsKey(p.packageName)) {
                continue
            }
            if (context.packageManager.getLaunchIntentForPackage(p.packageName) != null &&
                requiredPackages.containsKey(p.packageName)
            ) {
                val app = requiredPackages[p.packageName]
                val newInfo = AppInfo()
                newInfo.name =
                    if (app?.application?.iconText != null) app.application.iconText else p.loadLabel(context.packageManager)
                        .toString()
                newInfo.packageName = p.packageName
                newInfo.iconUrl = app?.application?.icon?.fullUrl
                newInfo.screenOrder = app?.screenOrder
                appInfos.add(newInfo)
            }
        }

        // Apply manually set order
        Collections.sort(appInfos, AppInfosComparator())

        return appInfos
    }

    private fun getConfiguredApps(
        context: Context,
        requiredPackages: MutableMap<String, ApplicationConfig>,
    ) {
        val config: SettingsHelper = SettingsHelper.getInstance(context)
        if (config.getConfig() != null) {
            val applications: List<ApplicationConfig> =
                SettingsHelper.getInstance(context).getConfig()!!.applications
            for (applicationConfig in applications) {
                if (applicationConfig.showIcon && !applicationConfig.remove) {
                    requiredPackages[applicationConfig.application.pkg] = applicationConfig
                }
            }
        }
    }

    inner class AppInfosComparator : Comparator<AppInfo> {
        override fun compare(o1: AppInfo, o2: AppInfo): Int {
            if (o1.screenOrder == null) {
                if (o2.screenOrder == null) {
                    return 0
                }
                return 1
            }
            if (o2.screenOrder == null) {
                return -1
            }
            return o1.screenOrder!!.compareTo(o2.screenOrder!!)
        }
    }

    companion object {
        var instance: AppShortcutManager? = null
            get() {
                if (field == null) {
                    field = AppShortcutManager()
                }
                return field
            }
            private set
    }
}