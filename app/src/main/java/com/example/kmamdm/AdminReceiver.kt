package com.example.kmamdm

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.PreferenceLogger

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        // We come here after both successful provisioning and manual activation of the device owner
        val preferences = context.applicationContext.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON).commit()
        Toast.makeText(context, "Device administrator activated", Toast.LENGTH_LONG).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val preferences = context.applicationContext.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
        preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF).commit()
        Toast.makeText(context, "Device administrator deactivated", Toast.LENGTH_LONG).show()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val preferences =
            context.applicationContext.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
        PreferenceLogger.log(preferences, "Profile provisioning complete")

        val bundle =
            intent.getParcelableExtra<PersistableBundle>(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        updateSettings(context, bundle)
    }

    companion object {
        fun updateSettings(context: Context, bundle: PersistableBundle?) {
            val preferences = context.applicationContext.getSharedPreferences(
                Const.PREFERENCES,
                Context.MODE_PRIVATE
            )
            try {
                val settingsHelper: SettingsHelper =
                    SettingsHelper.getInstance(context.applicationContext)
                var deviceId: String? = null
                PreferenceLogger.log(preferences, "Bundle != null: " + (bundle != null))
                if (bundle != null) {
                    deviceId = bundle.getString(Const.QR_DEVICE_ID_ATTR, null)
                    if (deviceId == null) {
                        // Also let's try legacy attribute
                        deviceId = bundle.getString(Const.QR_LEGACY_DEVICE_ID_ATTR, null)
                    }
                    if (deviceId == null) {
                        val deviceIdUse = bundle.getString(Const.QR_DEVICE_ID_USE_ATTR, null)
                        if (deviceIdUse != null) {
                            PreferenceLogger.log(preferences, "deviceIdUse: $deviceIdUse")
                            // Save for further automatic choice of the device ID
                            settingsHelper.setDeviceIdUse(deviceIdUse)
                        }
                    }
                }
                if (deviceId != null) {
                    // Device ID is delivered in the QR code!
                    // Added: "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": {"com.hmdm.DEVICE_ID": "(device id)"}
                    PreferenceLogger.log(preferences, "DeviceID: $deviceId")
                    settingsHelper.setDeviceId(deviceId)
                }

                var baseUrl: String? = null
                var secondaryBaseUrl: String? = null
                var serverProject: String? = null
                // val createOptions: DeviceCreateOptions = DeviceCreateOptions()
                if (bundle != null) {
                    baseUrl = bundle.getString(Const.QR_BASE_URL_ATTR, null)
//                    secondaryBaseUrl = bundle.getString(Const.QR_SECONDARY_BASE_URL_ATTR, null)
//                    serverProject = bundle.getString(Const.QR_SERVER_PROJECT_ATTR, null)
//                    createOptions.setCustomer(bundle.getString(Const.QR_CUSTOMER_ATTR, null))
//                    createOptions.setConfiguration(bundle.getString(Const.QR_CONFIG_ATTR, null))
//                    createOptions.setGroups(bundle.getString(Const.QR_GROUP_ATTR, null))
                    if (baseUrl != null) {
                        PreferenceLogger.log(preferences, "BaseURL: $baseUrl")
                        settingsHelper.setBaseUrl(baseUrl)
                    }
//                    if (secondaryBaseUrl != null) {
//                        PreferenceLogger.log(preferences, "SecondaryBaseURL: $secondaryBaseUrl")
//                        settingsHelper.setSecondaryBaseUrl(secondaryBaseUrl)
//                    }
//                    if (serverProject != null) {
//                        PreferenceLogger.log(preferences, "ServerPath: $serverProject")
//                        settingsHelper.setServerProject(serverProject)
//                    }
//                    if (createOptions.getCustomer() != null) {
//                        PreferenceLogger.log(
//                            preferences,
//                            "Customer: " + createOptions.getCustomer()
//                        )
//                        settingsHelper.setCreateOptionCustomer(createOptions.getCustomer())
//                    }
//                    if (createOptions.getConfiguration() != null) {
//                        PreferenceLogger.log(
//                            preferences,
//                            "Configuration: " + createOptions.getConfiguration()
//                        )
//                        settingsHelper.setCreateOptionConfigName(createOptions.getConfiguration())
//                    }
//                    if (createOptions.getGroups() != null) {
//                        PreferenceLogger.log(
//                            preferences,
//                            "Groups: " + bundle.getString(Const.QR_GROUP_ATTR)
//                        )
//                        settingsHelper.setCreateOptionGroup(createOptions.getGroupSet())
//                    }
                    settingsHelper.setQrProvisioning(true)
                }
            } catch (e: Exception) {
                // Ignored
                e.printStackTrace()
                PreferenceLogger.printStackTrace(preferences, e)
            }
        }
    }
}
