package com.hmdm.launcher.helper

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.kmamdm.BuildConfig
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.Application
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.InstallUtils
import com.example.kmamdm.utils.Utils
import java.net.URL

// Shared initialization code which should run either by MainActivity (in foreground mode)
// or by InitialSetupActivity (in background mode)
object Initializer {
    fun init(context: Context?) {
        Utils.lockSafeBoot(context)
        Utils.initPasswordReset(context)

        InstallUtils.clearTempFiles(context)

        // Install the certificates (repeat the action from InitialSetupActivity because
        // the customer may wish to install new certificates without re-enrolling the device
        CertInstaller.installCertificatesFromAssets(context)

        DetailedInfoWorker.schedule(context)
        if (BuildConfig.ENABLE_PUSH) {
            PushNotificationWorker.schedule(context)
        }
        ScheduledAppUpdateWorker.schedule(context)
    }

    fun startServicesAndLoadConfig(context: Context) {
        // Start Push service
        var pushOptions: String? = null
        var keepaliveTime: Int = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC
        val settingsHelper: SettingsHelper = SettingsHelper.getInstance(context)
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions()
            val newKeepaliveTime: Int = settingsHelper.getConfig().getKeepaliveTime()
            if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
                keepaliveTime = newKeepaliveTime
            }
        }
        if (BuildConfig.MQTT_SERVICE_FOREGROUND && BuildConfig.ENABLE_PUSH && pushOptions != null) {
            if (pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_WORKER
                || pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_ALARM
            ) {
                try {
                    val url = URL(settingsHelper.getBaseUrl())
                    // Broadcast receivers are not allowed to bind to services
                    // Therefore we start a service, and it binds to itself using
                    // PushNotificationMqttWrapper.getInstance().connect()
                    val serviceStartIntent = Intent()
                    serviceStartIntent.setClassName(context, MqttAndroidClient.SERVICE_NAME)
                    serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_START_AT_BOOT, true)
                    serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_DOMAIN, url.host)
                    serviceStartIntent.putExtra(
                        MqttAndroidClient.EXTRA_KEEPALIVE_TIME,
                        keepaliveTime
                    )
                    serviceStartIntent.putExtra(MqttAndroidClient.EXTRA_PUSH_OPTIONS, pushOptions)
                    serviceStartIntent.putExtra(
                        MqttAndroidClient.EXTRA_DEVICE_ID,
                        settingsHelper.getDeviceId()
                    )
                    val service: Any? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(
                            serviceStartIntent
                        ) else context.startService(serviceStartIntent)
                    Log.i(Const.LOG_TAG, "Starting Push service from BootReceiver")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (pushOptions == ServerConfig.PUSH_OPTIONS_POLLING) {
                try {
                    val serviceStartIntent = Intent(context, PushLongPollingService::class.java)
                    serviceStartIntent.putExtra(Const.EXTRA_ENABLED, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceStartIntent)
                    } else {
                        context.startService(serviceStartIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Start required services here instead of MainActivity (because it's not running)
        // Notice: some devices do not allow starting background services from boot receiver
        // java.lang.IllegalStateException
        // Not allowed to start service Intent { cmp=com.hmdm.launcher/.service.StatusControlService }: app is in background
        // Let's just ignore these exceptions for now
        val preferences =
            context.applicationContext.getSharedPreferences(Const.PREFERENCES, Context.MODE_PRIVATE)
        // Foreground apps checks are not available in a free version: services are the stubs
        if (preferences.getInt(
                Const.PREFERENCES_USAGE_STATISTICS,
                Const.PREFERENCES_OFF
            ) == Const.PREFERENCES_ON
        ) {
            try {
                context.startService(Intent(context, CheckForegroundApplicationService::class.java))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (preferences.getInt(
                Const.PREFERENCES_ACCESSIBILITY_SERVICE,
                Const.PREFERENCES_OFF
            ) == Const.PREFERENCES_ON
        ) {
            try {
                context.startService(
                    Intent(
                        context,
                        CheckForegroundAppAccessibilityService::class.java
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        try {
            context.startService(Intent(context, StatusControlService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }


        val uiNotifier: ConfigUpdater.UINotifier = object : ConfigUpdater.UINotifier {
            override fun onConfigUpdateStart() {
            }

            override fun onConfigUpdateServerError(errorText: String?) {
            }

            override fun onConfigUpdateNetworkError(errorText: String?) {
            }

            override fun onConfigLoaded() {
            }

            override fun onPoliciesUpdated() {
            }

            override fun onDownloadProgress(progress: Int, total: Long, current: Long) {
            }

            override fun onAppUpdateStart() {
            }

            override fun onAppRemoving(application: Application) {
            }

            override fun onAppDownloading(application: Application) {
            }

            override fun onAppInstalling(application: Application) {
            }

            override fun onAppDownloadError(application: Application?) {
            }

            override fun onAppInstallError(packageName: String?) {
            }

            override fun onAppInstallComplete(packageName: String?) {
            }

            override fun onConfigUpdateComplete() {
                // In background mode, we need to send the information to the server once update is complete
                val sendDeviceInfoTask: SendDeviceInfoTask = SendDeviceInfoTask(context)
                val deviceInfo: DeviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true)
                sendDeviceInfoTask.execute(deviceInfo)
                SendDeviceInfoWorker.scheduleDeviceInfoSending(context)
            }

            override fun onAllAppInstallComplete() {
            }
        }
        ConfigUpdater.forceConfigUpdate(context, uiNotifier, false)
    }

    // Used by InitialSetupActivity
    fun applyEarlyNonInteractivePolicies(context: Context?, config: ServerConfig) {
        if (config.getSystemUpdateType() != null && config.getSystemUpdateType() !== ServerConfig.SYSTEM_UPDATE_DEFAULT &&
            Utils.isDeviceOwner(context)
        ) {
            Utils.setSystemUpdatePolicy(
                context,
                config.getSystemUpdateType(),
                config.getSystemUpdateFrom(),
                config.getSystemUpdateTo()
            )
        }

        if (config.getBluetooth() != null) {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null) {
                    val enabled = bluetoothAdapter.isEnabled
                    if (config.getBluetooth() && !enabled) {
                        bluetoothAdapter.enable()
                    } else if (!config.getBluetooth() && enabled) {
                        bluetoothAdapter.disable()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (config.getTimeZone() != null) {
            Utils.setTimeZone(config.getTimeZone(), context)
        }

        if (config.getUsbStorage() != null) {
            Utils.lockUsbStorage(config.getUsbStorage(), context)
        }

        // Null value is processed here, it means unlock brightness
        Utils.setBrightnessPolicy(config.getAutoBrightness(), config.getBrightness(), context)

        if (config.getManageTimeout() != null) {
            Utils.setScreenTimeoutPolicy(config.getManageTimeout(), config.getTimeout(), context)
        }

        if (config.getManageVolume() != null && config.getManageVolume() && config.getVolume() != null) {
            Utils.lockVolume(false, context)
            if (!Utils.setVolume(config.getVolume(), context)) {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to set the device volume")
            }
        }

        if (config.getLockVolume() != null) {
            Utils.lockVolume(config.getLockVolume(), context)
        }

        Utils.disableScreenshots(config.isDisableScreenshots(), context)
    }
}