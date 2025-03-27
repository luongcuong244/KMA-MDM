package com.example.kmamdm.helper

import android.content.Context
import android.content.SharedPreferences
import com.example.kmamdm.model.ServerConfig
import com.fasterxml.jackson.databind.ObjectMapper

class SettingsHelper(context: Context) {
    private var sharedPreferences: SharedPreferences
    private var config: ServerConfig? = null
    private var oldConfig: ServerConfig? = null

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
                val mapper = ObjectMapper()
                config = mapper.readValue(
                    sharedPreferences.getString(PACKAGE_NAME + PREF_KEY_CONFIG, ""),
                    ServerConfig::class.java
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val isQrProvisioning: Boolean
        // Warning: this may return false if the launcher has been updated from older version
        get() = sharedPreferences.getBoolean(PACKAGE_NAME + PREF_QR_PROVISIONING, false)

    fun setQrProvisioning(value: Boolean): Boolean {
        return sharedPreferences.edit().putBoolean(PACKAGE_NAME + PREF_QR_PROVISIONING, value)
            .commit()
    }

    val isIntegratedProvisioningFlow: Boolean
        get() = sharedPreferences.getBoolean(
            PACKAGE_NAME + PREF_KEY_INTEGRATED_PROVISIONING_FLOW,
            false
        )

    fun setIntegratedProvisioningFlow(value: Boolean): Boolean {
        return sharedPreferences.edit()
            .putBoolean(PACKAGE_NAME + PREF_KEY_INTEGRATED_PROVISIONING_FLOW, value).commit()
    }

    fun setServerProject(serverProject: String?): Boolean {
        return sharedPreferences.edit()
            .putString(PACKAGE_NAME + PREF_KEY_SERVER_PROJECT, serverProject).commit()
    }

    val deviceId: String
        get() = sharedPreferences.getString(PACKAGE_NAME + PREF_KEY_DEVICE_ID, "")!!

    fun setDeviceId(deviceId: String?): Boolean {
        return sharedPreferences.edit().putString(PACKAGE_NAME + PREF_KEY_DEVICE_ID, deviceId)
            .commit()
    }

    val isMainActivityRunning: Boolean
        get() = sharedPreferences.getBoolean(PACKAGE_NAME + PREF_KEY_ACTIVITY_RUNNING, false)

    fun setMainActivityRunning(running: Boolean): Boolean {
        return sharedPreferences.edit()
            .putBoolean(PACKAGE_NAME + PREF_KEY_ACTIVITY_RUNNING, running).commit()
    }

    val isRestoreLauncher: Boolean
        get() = sharedPreferences.getBoolean(PACKAGE_NAME + PREF_KEY_RESTORE_LAUNCHER, false)

    fun setRestoreLauncher(restore: Boolean): Boolean {
        return sharedPreferences.edit()
            .putBoolean(PACKAGE_NAME + PREF_KEY_RESTORE_LAUNCHER, restore).commit()
    }

    val configUpdateTimestamp: Long
        get() = sharedPreferences.getLong(PACKAGE_NAME + PREF_CFG_UPDATE_TIMESTAMP, 0)

    fun setConfigUpdateTimestamp(timestamp: Long): Boolean {
        return sharedPreferences.edit().putLong(PACKAGE_NAME + PREF_CFG_UPDATE_TIMESTAMP, timestamp)
            .commit()
    }

    fun setCreateOptionCustomer(customer: String?): Boolean {
        return if (customer == null) {
            sharedPreferences.edit().remove(PACKAGE_NAME + PREF_KEY_CUSTOMER).commit()
        } else {
            sharedPreferences.edit().putString(PACKAGE_NAME + PREF_KEY_CUSTOMER, customer).commit()
        }
    }

    val createOptionCustomer: String?
        get() = sharedPreferences.getString(PACKAGE_NAME + PREF_KEY_CUSTOMER, null)

    fun setDeviceIdUse(deviceIdUse: String?): Boolean {
        return if (deviceIdUse == null) {
            sharedPreferences.edit().remove(PACKAGE_NAME + PREF_KEY_DEVICE_ID_USE).commit()
        } else {
            sharedPreferences.edit().putString(PACKAGE_NAME + PREF_KEY_DEVICE_ID_USE, deviceIdUse)
                .commit()
        }
    }

    val deviceIdUse: String?
        get() = sharedPreferences.getString(PACKAGE_NAME + PREF_KEY_DEVICE_ID_USE, null)

    fun setLastAppUpdateState(lastAppUpdateState: Boolean): Boolean {
        return sharedPreferences.edit()
            .putBoolean(PACKAGE_NAME + PREF_KEY_LAST_APP_UPDATE_STATE, lastAppUpdateState).commit()
    }

    val lastAppUpdateState: Boolean
        get() = sharedPreferences.getBoolean(PACKAGE_NAME + PREF_KEY_LAST_APP_UPDATE_STATE, false)

    fun setAppStartTime(time: Long): Boolean {
        return sharedPreferences.edit().putLong(PACKAGE_NAME + PREF_KEY_APP_START_TIME, time)
            .commit()
    }

    val appStartTime: Long
        get() = sharedPreferences.getLong(PACKAGE_NAME + PREF_KEY_APP_START_TIME, 0)

    fun setCreateOptionConfigName(configName: String?): Boolean {
        return if (configName == null) {
            sharedPreferences.edit().remove(PACKAGE_NAME + PREF_KEY_CONFIG_NAME).commit()
        } else {
            sharedPreferences.edit().putString(PACKAGE_NAME + PREF_KEY_CONFIG_NAME, configName)
                .commit()
        }
    }

    val createOptionConfigName: String?
        get() = sharedPreferences.getString(PACKAGE_NAME + PREF_KEY_CONFIG_NAME, null)

    fun setCreateOptionGroup(group: Set<String?>?): Boolean {
        return if (group == null) {
            sharedPreferences.edit().remove(PACKAGE_NAME + PREF_KEY_GROUP).commit()
        } else {
            sharedPreferences.edit().putStringSet(PACKAGE_NAME + PREF_KEY_GROUP, group).commit()
        }
    }

    val createOptionGroup: Set<String>?
        get() = sharedPreferences.getStringSet(PACKAGE_NAME + PREF_KEY_GROUP, null)

    fun updateConfig(config: ServerConfig?) {
        try {
            val objectMapper = ObjectMapper()
            sharedPreferences.edit()
                .putString(PACKAGE_NAME + PREF_KEY_CONFIG, objectMapper.writeValueAsString(config))
                .commit()
        } catch (e: Exception) {
            e.printStackTrace()
            // Do not apply changes when there's an error while writing settings
            return
        }
        this.oldConfig = this.config
        this.config = config
    }

    fun getConfig(): ServerConfig? {
        return config
    }

    companion object {
        private const val PREFERENCES_ID = ".helpers.PREFERENCES"
        private const val PREF_KEY_BASE_URL = ".helpers.BASE_URL"
        private const val PREF_KEY_SECONDARY_BASE_URL = ".helpers.SECONDARY_BASE_URL"
        private const val PREF_KEY_SERVER_PROJECT = ".helpers.SERVER_PROJECT"
        private const val PREF_KEY_DEVICE_ID = ".helpers.DEVICE_ID"
        private const val PREF_KEY_CUSTOMER = ".helpers.CUSTOMER"
        private const val PREF_KEY_CONFIG_NAME = ".helpers.CONFIG_NAME"
        private const val PREF_KEY_GROUP = ".helpers.GROUP"
        private const val PREF_KEY_DEVICE_ID_USE = ".helpers.DEVICE_ID_USE"
        private const val PREF_KEY_CONFIG = ".helpers.CONFIG"
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