package com.example.kmamdm.helper

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.ConditionVariable
import android.util.Log
import android.widget.Toast
import com.example.kmamdm.model.Application
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.server.json.ServerConfigResponse
import com.example.kmamdm.server.repository.ConfigurationRepository
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.InstallUtils
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
        fun onAppRemoving(application: Application?)
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
            appInstallReceiver = null
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


        // Xoa bo han che uninstall app khi cap nhat config

//        // Work around a strange bug with stale SettingsHelper instance: re-read its value
//        settingsHelper = SettingsHelper.getInstance(context.applicationContext)
//
//        if (settingsHelper!!.getConfig() != null && settingsHelper!!.getConfig()
//                .getRestrictions() != null
//        ) {
//            Utils.releaseUserRestrictions(context, settingsHelper!!.getConfig().getRestrictions())
//            // Explicitly release restrictions of installing/uninstalling apps
//            Utils.releaseUserRestrictions(context, "no_install_apps,no_uninstall_apps")
//        }

        uiNotifier?.onConfigUpdateStart()
        ConfigurationRepository.getServerConfig(object : Callback<ServerConfigResponse> {
            override fun onResponse(
                call: Call<ServerConfigResponse>,
                response: Response<ServerConfigResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val config = response.body()!!.data
                    registerAppInstallReceiver()

                    CoroutineScope(Dispatchers.IO).launch {
                        checkAndInstallApplications(config)
                        withContext(Dispatchers.Main) {
                            Log.d(Const.LOG_TAG, "Config updated")
                            // save config to share pref
                            SettingsHelper.getInstance(context).updateConfig(config)
                            uiNotifier?.onConfigUpdateComplete()
                        }
                    }
                }
            }

            override fun onFailure(call: Call<ServerConfigResponse>, t: Throwable) {
                Toast.makeText(context, "Get failed", Toast.LENGTH_SHORT).show()
                uiNotifier?.onConfigUpdateServerError(t.message)
            }
        })
    }

    suspend fun checkAndInstallApplications(config: ServerConfig) = withContext(Dispatchers.IO) {
        val applications = config.applications
        for (application in applications) {
            // Check if the app is already installed and update is needed
            if (application.url != null && shouldUpdateApplication(application)) {
                withContext(Dispatchers.Main) {
                    uiNotifier?.onAppDownloading(application)
                }
                val downloadedFile = InstallUtils.downloadFile(
                    context!!,
                    application.url,
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

    private fun shouldUpdateApplication(application: Application): Boolean {
        val oldConfig = SettingsHelper.getInstance(context!!).getConfig()
        val oldConfigApplications = oldConfig?.applications ?: emptyList()
        val oldApplication = oldConfigApplications.find { it.pkg == application.pkg }
        return oldApplication == null || !InstallUtils.areVersionsEqual(
            application.versionName,
            application.versionCode,
            oldApplication.versionName,
            oldApplication.versionCode
        )
    }
}