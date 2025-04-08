package com.example.kmamdm.helper

import android.content.Context
import android.content.SharedPreferences
import com.example.kmamdm.model.ApplicationSetting
import com.example.kmamdm.model.ServerConfig
import com.google.gson.Gson

class SettingsHelper(context: Context) {
    private var sharedPreferences: SharedPreferences
    private var config: ServerConfig? = null
    private val appSettings: Map<String, ApplicationSetting> = HashMap<String, ApplicationSetting>()

    init {
        PACKAGE_NAME = context.packageName
        sharedPreferences =
            context.getSharedPreferences(PACKAGE_NAME + PREFERENCES_ID, Context.MODE_PRIVATE)
        initConfig()
    }

    fun refreshConfig(context: Context) {
        if (config == null) {
            sharedPreferences =
                context.getSharedPreferences(PACKAGE_NAME + PREFERENCES_ID, Context.MODE_PRIVATE)
            initConfig()
        }
    }

    private fun initConfig() {
        try {
            if (sharedPreferences.contains(PACKAGE_NAME + PREF_KEY_CONFIG)) {
                val json = sharedPreferences.getString(PACKAGE_NAME + PREF_KEY_CONFIG, "")
                val gson = Gson()
                config = gson.fromJson(json, ServerConfig::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateConfig(config: ServerConfig?) {
        val gson = Gson()
        val json = gson.toJson(config) // Chuyển object thành JSON
        sharedPreferences.edit()
            .putString(PACKAGE_NAME + PREF_KEY_CONFIG, json)
            .commit()
        this.config = config
    }

    fun getConfig(): ServerConfig? {
        return config
    }

    fun isKioskModeRunning(): Boolean {
        return sharedPreferences.getBoolean(
            PACKAGE_NAME + PREF_KEY_KIOSK_MODE_RUNNING,
            false
        )
    }

    fun setKioskModeRunning(isRunning: Boolean) {
        sharedPreferences.edit()
            .putBoolean(PACKAGE_NAME + PREF_KEY_KIOSK_MODE_RUNNING, isRunning)
            .commit()
    }

    companion object {
        private const val PREFERENCES_ID = ".helpers.PREFERENCES"
        private const val PREF_KEY_CONFIG = ".helpers.CONFIG"
        private const val PREF_KEY_KIOSK_MODE_RUNNING = ".helpers.KIOSK_MODE_RUNNING"

        private const val PREF_KEY_BASE_URL = ".helpers.BASE_URL"
        private const val PREF_KEY_SECONDARY_BASE_URL = ".helpers.SECONDARY_BASE_URL"
        private const val PREF_KEY_SERVER_PROJECT = ".helpers.SERVER_PROJECT"
        private const val PREF_KEY_DEVICE_ID = ".helpers.DEVICE_ID"
        private const val PREF_KEY_CUSTOMER = ".helpers.CUSTOMER"
        private const val PREF_KEY_CONFIG_NAME = ".helpers.CONFIG_NAME"
        private const val PREF_KEY_GROUP = ".helpers.GROUP"
        private const val PREF_KEY_DEVICE_ID_USE = ".helpers.DEVICE_ID_USE"
        private const val PREF_KEY_IP_ADDRESS = ".helpers.IP_ADDRESS"
        private const val PREF_QR_PROVISIONING = ".helpers.QR_PROVISIONING"
        private const val PREF_CFG_UPDATE_TIMESTAMP = ".helpers.CFG_UPDATE_TIMESTAMP"
        private const val PREF_KEY_ACTIVITY_RUNNING = ".helpers.ACTIVITY_RUNNING"
        private const val PREF_KEY_RESTORE_LAUNCHER = ".helpers.NEED_LAUNCHER_RESET"
        private const val PREF_KEY_INTEGRATED_PROVISIONING_FLOW =
            ".helpers.INTEGRATED_PROVISIONING_FLOW"
        private const val PREF_KEY_LAST_APP_UPDATE_STATE = ".helpers.LAST_APP_UPDATE_STATE"
        private const val PREF_KEY_APP_START_TIME = ".helpers.APP_START_TIME"

        // This prefix is for the compatibility with a legacy package name
        private var PACKAGE_NAME: String? = null

        private var instance: SettingsHelper? = null

        fun getInstance(context: Context): SettingsHelper {
            if (instance == null) {
                instance = SettingsHelper(context)
            }

            return instance!!
        }
    }
}