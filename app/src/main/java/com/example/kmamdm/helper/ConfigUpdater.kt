package com.example.kmamdm.helper

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.ConditionVariable
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.kmamdm.BuildConfig
import com.example.kmamdm.model.Application
import com.example.kmamdm.model.ApplicationConfig
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.server.json.ServerConfigResponse
import com.example.kmamdm.server.repository.ConfigurationRepository
import com.example.kmamdm.socket.SocketManager
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.DeviceUtils
import com.example.kmamdm.utils.InstallUtils
import com.example.kmamdm.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ConfigUpdater {
    interface UINotifier {
        fun onConfigUpdateStart()
        fun onConfigUpdateServerError(errorText: String?)
        fun onConfigUpdateNetworkError(errorText: String?)
        fun onConfigLoaded()
        fun onPoliciesUpdated()

        // fun onFileDownloading(remoteFile: RemoteFile?)
        fun onDownloadProgress(progress: Int, total: Long, current: Long)

        //        fun onFileDownloadError(remoteFile: RemoteFile?)
//        fun onFileInstallError(remoteFile: RemoteFile?)
        fun onAppUpdateStart()
        fun onAppRemoving(application: Application)
        fun onAppDownloading(application: Application)
        fun onAppInstalling(application: Application)
        fun onAppDownloadError(application: Application?)
        fun onAppInstallError(packageName: String?)
        fun onAppInstallComplete(packageName: String?)
        fun onConfigUpdateComplete()
        fun onAllAppInstallComplete()
    }

    private var configInitializing = false
    private var context: Context? = null
    private var uiNotifier: UINotifier? = null
    private var userInteraction = false
    private var appInstallReceiver: BroadcastReceiver? = null
    private var conditionVariable = ConditionVariable()
    private val applicationsForRun = mutableListOf<Application>()

    companion object {
        fun forceConfigUpdate(context: Context, notifier: UINotifier?, userInteraction: Boolean) {
            ConfigUpdater().updateConfig(context, notifier, userInteraction)
        }
    }

    private fun registerAppInstallReceiver() {
        if (appInstallReceiver == null) {
            Log.d(Const.LOG_TAG, "Install completion receiver prepared")
            appInstallReceiver = object : BroadcastReceiver() {
                @SuppressLint("UnsafeIntentLaunch")
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Const.ACTION_INSTALL_COMPLETE) {
                        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                val confirmationIntent =
                                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)

                                // Fix the Intent Redirection vulnerability
                                // https://support.google.com/faqs/answer/9267555
                                val name =
                                    confirmationIntent!!.resolveActivity(context.packageManager)
                                val flags = confirmationIntent.flags
                                if (name != null && name.packageName != context.packageName && (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0 && (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
                                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        context.startActivity(confirmationIntent)
                                    } catch (e: Exception) {
                                    }
                                } else {
                                    Log.e(
                                        Const.LOG_TAG,
                                        "Intent redirection detected, ignoring the fault intent!"
                                    )
                                }
                            }

                            PackageInstaller.STATUS_SUCCESS -> {
                                Log.d(
                                    Const.LOG_TAG,
                                    "Install success: ${intent.getStringExtra(Const.PACKAGE_NAME)}"
                                )
                                val packageName = intent.getStringExtra(Const.PACKAGE_NAME)
                                uiNotifier?.onAppInstallComplete(packageName)
                                conditionVariable.open()
                            }

                            else -> {
                                Log.e(Const.LOG_TAG, "Install error: $status")
                                val packageName = intent.getStringExtra(Const.PACKAGE_NAME)
                                uiNotifier?.onAppInstallError(packageName)
                                conditionVariable.open()
                            }
                        }
                    }
                }
            }
        } else {
            // Renewed the configuration multiple times?
            unregisterAppInstallReceiver()
        }

        try {
            Log.d(Const.LOG_TAG, "Install completion receiver registered")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context!!.registerReceiver(
                    appInstallReceiver,
                    IntentFilter(Const.ACTION_INSTALL_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context!!.registerReceiver(
                    appInstallReceiver,
                    IntentFilter(Const.ACTION_INSTALL_COMPLETE)
                )
            }
        } catch (e: Exception) {
            // On earlier Android versions (4, 5):
            // Fatal Exception: android.content.ReceiverCallNotAllowedException
            // BroadcastReceiver components are not allowed to register to receive intents
            e.printStackTrace()
        }
    }

    private fun unregisterAppInstallReceiver() {
        if (appInstallReceiver != null) {
            try {
                Log.d(Const.LOG_TAG, "Install completion receiver unregistered")
                context!!.unregisterReceiver(appInstallReceiver)
            } catch (e: Exception) {
                // Receiver not registered
                e.printStackTrace()
            }
        }
    }

    fun updateConfig(context: Context, uiNotifier: UINotifier?, userInteraction: Boolean) {
        if (configInitializing) {
            Log.i(Const.LOG_TAG, "updateConfig(): configInitializing=true, exiting")
            return
        }

        Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=true")
        configInitializing = true
        this.context = context
        this.uiNotifier = uiNotifier
        this.userInteraction = userInteraction

        val settingsHelper = SettingsHelper.getInstance(context.applicationContext)

        if (settingsHelper.getConfig() != null && settingsHelper.getConfig()!!.restrictions != null) {
            Utils.releaseUserRestrictions(context, settingsHelper.getConfig()!!.restrictions!!)
            // Explicitly release restrictions of installing/uninstalling apps
            Utils.releaseUserRestrictions(
                context,
                "${UserManager.DISALLOW_INSTALL_APPS},${UserManager.DISALLOW_UNINSTALL_APPS}"
            )
        }

        uiNotifier?.onConfigUpdateStart()

        val deviceId = settingsHelper.getDeviceId() ?: ""
        var signature = ""
        try {
            signature = CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + deviceId)
        } catch (e: java.lang.Exception) {
        }

        val deviceInfo = DeviceUtils.getDeviceInfo(context)

        ConfigurationRepository.getServerConfig(
            signature,
            deviceId,
            deviceInfo,
            object : Callback<ServerConfigResponse> {
                override fun onResponse(
                    call: Call<ServerConfigResponse>,
                    response: Response<ServerConfigResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        // connect socket
                        if (!SocketManager.isSocketConnected) {
                            SocketManager.get().destroy()
                            SocketManager.get().openSocket(context)
                        }

                        val config = response.body()!!.data
                        registerAppInstallReceiver()

                        CoroutineScope(Dispatchers.IO).launch {
                            checkAndUninstallApplication(config)
                            checkAndInstallApplications(config)
                            checkApplicationsForRun(config)
                            setDefaultLauncher(context, config)
                            lockRestrictions(config)
                            withContext(Dispatchers.Main) {
                                Log.d(Const.LOG_TAG, "Config updated")
                                configInitializing = false

                                // check permission draw over other apps because we need to draw exit kiosk button on top of other apps
                                if ((settingsHelper.getConfig()?.kioskApps
                                        ?: listOf()).isNotEmpty() && !Settings.canDrawOverlays(
                                        context
                                    )
                                ) {
                                    Log.d(
                                        Const.LOG_TAG,
                                        "Config updated: draw over other apps permission is not granted"
                                    )
                                    settingsHelper.getConfig()?.kioskMode = false
                                    settingsHelper.updateConfig(settingsHelper.getConfig())
                                }

                                // save config to share pref
                                settingsHelper.updateConfig(config)
                                uiNotifier?.onConfigUpdateComplete()
                            }
                        }
                    } else {
                        val errorText = response.errorBody()?.string()
                        Log.e(
                            Const.LOG_TAG,
                            "Config update failedd: $errorText"
                        )
                        uiNotifier?.onConfigUpdateServerError(errorText)
                        configInitializing = false
                    }
                }

                override fun onFailure(call: Call<ServerConfigResponse>, t: Throwable) {
                    Log.e(
                        Const.LOG_TAG,
                        "Config update failed: ${t.message}"
                    )
                    uiNotifier?.onConfigUpdateNetworkError(t.message)
                    configInitializing = false
                }
            },
        )
    }

    suspend fun checkAndUninstallApplication(config: ServerConfig) = withContext(Dispatchers.IO) {
        val applications = config.applications
        for (applicationConfig in applications) {
            // if the url is null -> system app -> do not uninstall
            val application = applicationConfig.application
            if (applicationConfig.version.fullUrl != null && applicationConfig.remove) {
                if (isAppInstalled(context!!, application.pkg)) {
                    withContext(Dispatchers.Main) {
                        uiNotifier?.onAppRemoving(application)
                    }
                    // Đóng conditionVariable trước khi block để đảm bảo nó chặn đúng cách
                    conditionVariable.close()
                    InstallUtils.silentUninstallApplication(context!!, application.pkg)
                    conditionVariable.block()
                }
            }
        }
    }

    suspend fun checkAndInstallApplications(config: ServerConfig) = withContext(Dispatchers.IO) {
        val isGoodTimeForAppUpdate = userInteraction
        val isGoodNetworkForUpdate = userInteraction

        if (!isGoodTimeForAppUpdate || !isGoodNetworkForUpdate) {
            Log.d(Const.LOG_TAG, "Not good time for app update")
            return@withContext
        }

        val applications = config.applications
        for (applicationConfig in applications) {
            // Check if the app is already installed and update is needed
            val application = applicationConfig.application
            if (applicationConfig.version.fullUrl != null && !applicationConfig.remove) {
                if (isAppInstalled(context!!, application.pkg) && !shouldUpdateApplication(
                        applicationConfig
                    )
                ) {
                    continue
                }
                withContext(Dispatchers.Main) {
                    uiNotifier?.onAppDownloading(application)
                }
                val downloadedFile = InstallUtils.downloadFile(
                    context!!,
                    applicationConfig.version.fullUrl!!,
                    object : InstallUtils.DownloadProgress {
                        override fun onDownloadProgress(progress: Int, total: Long, current: Long) {
                            uiNotifier?.onDownloadProgress(progress, total, current)
                        }
                    })
                withContext(Dispatchers.Main) {
                    uiNotifier?.onAppInstalling(application)
                }

                // Đóng conditionVariable trước khi block để đảm bảo nó chặn đúng cách
                conditionVariable.close()

                InstallUtils.silentInstallApplication(
                    context!!,
                    downloadedFile,
                    application.pkg,
                    object : InstallUtils.InstallErrorHandler {
                        override fun onInstallError(msg: String?) {
                            if (downloadedFile.exists()) {
                                downloadedFile.delete()
                            }
                            if (uiNotifier != null) {
                                uiNotifier!!.onAppInstallError(application.pkg)
                            }
                        }
                    })
                conditionVariable.block()
            }
        }
    }

    suspend fun checkApplicationsForRun(config: ServerConfig) = withContext(Dispatchers.IO) {
        applicationsForRun.clear()
        val applications = config.applications
        for (applicationConfig in applications) {
            if (applicationConfig.runAfterInstall) {
                applicationsForRun.add(applicationConfig.application)
            }
        }
    }

    suspend fun setDefaultLauncher(context: Context, config: ServerConfig) =
        withContext(Dispatchers.IO) {
            if (Utils.isDeviceOwner(context)) {
                // "Run default launcher" means we should not set KMA MDM as a default launcher
                // and clear the setting if it has been already set
                val needSetLauncher =
                    config.runDefaultLauncher == null || !config.runDefaultLauncher
                val defaultLauncher: String? = Utils.getDefaultLauncher(context)
                defaultLauncher?.let {
                    if (needSetLauncher && !context.packageName.equals(
                            defaultLauncher,
                            ignoreCase = true
                        )
                    ) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Set default launcher: ${context.packageName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Utils.setDefaultLauncher(context)
                    } else if (!needSetLauncher && context.packageName.equals(
                            defaultLauncher,
                            ignoreCase = true
                        )
                    ) {
                        Utils.clearDefaultLauncher(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Clear default launcher: ${context.packageName}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

    private fun lockRestrictions(config: ServerConfig) {
        config.restrictions?.let {
            Utils.lockUserRestrictions(context!!, config.restrictions)
        }
    }

    private fun shouldUpdateApplication(application: ApplicationConfig): Boolean {
        val oldConfig = SettingsHelper.getInstance(context!!).getConfig()
        val oldConfigApplications = oldConfig?.applications ?: emptyList()
        val oldApplication =
            oldConfigApplications.find { it.application.pkg == application.application.pkg && !it.remove }
        return oldApplication == null || !InstallUtils.areVersionsEqual(
            application.version.versionName,
            application.version.versionCode,
            oldApplication.version.versionName,
            oldApplication.version.versionCode
        )
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getApplicationsForRun(): MutableList<Application> {
        return applicationsForRun
    }
}