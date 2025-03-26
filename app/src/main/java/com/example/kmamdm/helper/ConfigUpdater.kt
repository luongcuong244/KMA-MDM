package com.hmdm.launcher.helper

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.hmdm.launcher.BuildConfig
import com.hmdm.launcher.Const
import com.hmdm.launcher.db.DatabaseHelper
import com.hmdm.launcher.db.DownloadTable
import com.hmdm.launcher.db.RemoteFileTable
import com.hmdm.launcher.json.Application
import com.hmdm.launcher.json.DeviceInfo
import com.hmdm.launcher.json.Download
import com.hmdm.launcher.json.PushMessage
import com.hmdm.launcher.json.RemoteFile
import com.hmdm.launcher.json.ServerConfig
import com.hmdm.launcher.pro.worker.DetailedInfoWorker
import com.hmdm.launcher.server.ServerServiceKeeper
import com.hmdm.launcher.service.PushLongPollingService
import com.hmdm.launcher.task.ConfirmDeviceResetTask
import com.hmdm.launcher.task.ConfirmPasswordResetTask
import com.hmdm.launcher.task.ConfirmRebootTask
import com.hmdm.launcher.task.GetRemoteLogConfigTask
import com.hmdm.launcher.task.GetServerConfigTask
import com.hmdm.launcher.util.DeviceInfoProvider
import com.hmdm.launcher.util.InstallUtils
import com.hmdm.launcher.util.PushNotificationMqttWrapper
import com.hmdm.launcher.util.RemoteLogger
import com.hmdm.launcher.util.SystemUtils
import com.hmdm.launcher.util.Utils
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.LinkedList

class ConfigUpdater {
    interface UINotifier {
        fun onConfigUpdateStart()
        fun onConfigUpdateServerError(errorText: String?)
        fun onConfigUpdateNetworkError(errorText: String?)
        fun onConfigLoaded()
        fun onPoliciesUpdated() 
        fun onFileDownloading(remoteFile: RemoteFile?)
        fun onDownloadProgress(progress: Int, total: Long, current: Long)
        fun onFileDownloadError(remoteFile: RemoteFile?)
        fun onFileInstallError(remoteFile: RemoteFile?)
        fun onAppUpdateStart()
        fun onAppRemoving(application: Application?)
        fun onAppDownloading(application: Application?)
        fun onAppInstalling(application: Application?)
        fun onAppDownloadError(application: Application?)
        fun onAppInstallError(packageName: String?)
        fun onAppInstallComplete(packageName: String?)
        fun onConfigUpdateComplete()
        fun onAllAppInstallComplete()
    }

    private var configInitializing = false
    private var context: Context? = null
    private var uiNotifier: UINotifier? = null
    private var settingsHelper: SettingsHelper? = null
    private val handler = Handler(Looper.getMainLooper())
    private val filesForInstall: MutableList<RemoteFile?> = LinkedList<Any?>()
    private val applicationsForInstall: MutableList<Application?> = LinkedList<Any?>()
    private val applicationsForRun: MutableList<Application?> = LinkedList<Any?>()
    private val pendingInstallations: MutableMap<String, File> = HashMap()
    private var appInstallReceiver: BroadcastReceiver? = null
    private var retry = true
    private var loadOnly = false
    private var userInteraction = false

    fun getApplicationsForRun(): List<Application?> {
        return applicationsForRun
    }

    fun setLoadOnly(loadOnly: Boolean) {
        this.loadOnly = loadOnly
    }

    fun updateConfig(context: Context, uiNotifier: UINotifier?, userInteraction: Boolean) {
        if (configInitializing) {
            Log.i(Const.LOG_TAG, "updateConfig(): configInitializing=true, exiting")
            return
        }

        Log.i(Const.LOG_TAG, "updateConfig(): set configInitializing=true")
        configInitializing = true
        DetailedInfoWorker.requestConfigUpdate(context)
        this.context = context
        this.uiNotifier = uiNotifier
        this.userInteraction = userInteraction

        // Work around a strange bug with stale SettingsHelper instance: re-read its value
        settingsHelper = SettingsHelper.getInstance(context.applicationContext)

        if (settingsHelper.getConfig() != null && settingsHelper.getConfig()
                .getRestrictions() != null
        ) {
            Utils.releaseUserRestrictions(context, settingsHelper.getConfig().getRestrictions())
            // Explicitly release restrictions of installing/uninstalling apps
            Utils.releaseUserRestrictions(context, "no_install_apps,no_uninstall_apps")
        }

        uiNotifier?.onConfigUpdateStart()
        object : GetServerConfigTask(context) {
            protected override fun onPostExecute(result: Int?) {
                super.onPostExecute(result)
                configInitializing = false
                Log.i(
                    Const.LOG_TAG,
                    "updateConfig(): set configInitializing=false after getting config"
                )

                when (result) {
                    Const.TASK_SUCCESS -> {
                        RemoteLogger.log(context, Const.LOG_INFO, "Configuration updated")
                        updateRemoteLogConfig()
                    }

                    Const.TASK_ERROR -> {
                        RemoteLogger.log(
                            context,
                            Const.LOG_WARN,
                            "Failed to update config: server error"
                        )
                        uiNotifier?.onConfigUpdateServerError(getErrorText())
                    }

                    Const.TASK_NETWORK_ERROR -> {
                        RemoteLogger.log(
                            context,
                            Const.LOG_WARN,
                            "Failed to update config: network error"
                        )
                        if (retry) {
                            // Retry the request once because WiFi may not yet be initialized
                            retry = false
                            handler.postDelayed({
                                updateConfig(
                                    context,
                                    uiNotifier,
                                    userInteraction
                                )
                            }, 15000)
                        } else {
                            if (settingsHelper.getConfig() != null && !userInteraction) {
                                if (uiNotifier != null && settingsHelper.getConfig().isShowWifi()) {
                                    // Show network error dialog with Wi-Fi settings
                                    // if it is required by the web panel
                                    // so the user can set up WiFi even in kiosk mode
                                    uiNotifier.onConfigUpdateNetworkError(getErrorText())
                                } else {
                                    updateRemoteLogConfig()
                                }
                            } else {
                                uiNotifier?.onConfigUpdateNetworkError(getErrorText())
                            }
                        }
                    }
                }
            }
        }.execute()
    }

    fun skipConfigLoad() {
        updateRemoteLogConfig()
    }

    private fun updateRemoteLogConfig() {
        Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): get logging configuration")

        val task: GetRemoteLogConfigTask = object : GetRemoteLogConfigTask(context) {
            protected override fun onPostExecute(result: Int) {
                super.onPostExecute(result)
                Log.i(Const.LOG_TAG, "updateRemoteLogConfig(): result=$result")
                val deviceOwner: Boolean = Utils.isDeviceOwner(context)
                RemoteLogger.log(context, Const.LOG_INFO, "Device owner: $deviceOwner")
                if (deviceOwner) {
                    setSelfPermissions(
                        if (settingsHelper.getConfig() != null) settingsHelper.getConfig()
                            .getAppPermissions() else null
                    )
                }
                try {
                    if (settingsHelper.getConfig() != null && uiNotifier != null) {
                        uiNotifier!!.onConfigLoaded()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                if (!loadOnly) {
                    checkServerMigration()
                } else {
                    Log.d(Const.LOG_TAG, "LoadOnly flag set, finishing the update flow")
                }
                // If loadOnly flag is set, we finish the flow here
            }
        }
        task.execute()
    }

    private fun setSelfPermissions(appPermissionStrategy: String?) {
        Utils.autoGrantRequestedPermissions(
            context, context!!.packageName,
            appPermissionStrategy, true
        )
    }

    private fun checkServerMigration() {
        if (settingsHelper != null && settingsHelper.getConfig() != null && settingsHelper.getConfig()
                .getNewServerUrl() != null &&
            !settingsHelper.getConfig().getNewServerUrl().trim().equals("")
        ) {
            try {
                val migrationHelper: MigrationHelper =
                    MigrationHelper(settingsHelper.getConfig().getNewServerUrl().trim())
                if (migrationHelper.needMigrating(context)) {
                    // Before migration, test that new URL is working well
                    migrationHelper.tryNewServer(context, object : CompletionHandler() {
                        override fun onSuccess() {
                            // Everything is OK, migrate!
                            RemoteLogger.log(
                                context,
                                Const.LOG_INFO,
                                "Migrated to " + settingsHelper.getConfig().getNewServerUrl().trim()
                            )
                            settingsHelper.setBaseUrl(migrationHelper.getBaseUrl())
                            settingsHelper.setSecondaryBaseUrl(migrationHelper.getBaseUrl())
                            settingsHelper.setServerProject(migrationHelper.getServerProject())
                            ServerServiceKeeper.resetServices()
                            configInitializing = false
                            updateConfig(context!!, uiNotifier, false)
                        }

                        override fun onError(cause: String) {
                            RemoteLogger.log(
                                context,
                                Const.LOG_WARN,
                                ("Failed to migrate to " + settingsHelper.getConfig()
                                    .getNewServerUrl().trim()).toString() + ": " + cause
                            )
                            setupPushService()
                        }
                    })
                    return
                }
            } catch (e: Exception) {
                // Malformed URL
                RemoteLogger.log(
                    context,
                    Const.LOG_WARN,
                    ("Failed to migrate to " + settingsHelper.getConfig().getNewServerUrl()
                        .trim()).toString() + ": malformed URL"
                )
            }
        }
        setupPushService()
    }

    private fun setupPushService() {
        Log.d(Const.LOG_TAG, "setupPushService() called")
        var pushOptions: String? = null
        var keepaliveTime: Int = Const.DEFAULT_PUSH_ALARM_KEEPALIVE_TIME_SEC
        if (settingsHelper != null && settingsHelper.getConfig() != null) {
            pushOptions = settingsHelper.getConfig().getPushOptions()
            val newKeepaliveTime: Int = settingsHelper.getConfig().getKeepaliveTime()
            if (newKeepaliveTime != null && newKeepaliveTime >= 30) {
                keepaliveTime = newKeepaliveTime
            }
        }
        if (BuildConfig.ENABLE_PUSH && pushOptions != null) {
            if (pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_WORKER
                || pushOptions == ServerConfig.PUSH_OPTIONS_MQTT_ALARM
            ) {
                try {
                    val url = URL(settingsHelper.getBaseUrl())
                    val nextRunnable = Runnable {
                        checkFactoryReset()
                    }
                    PushNotificationMqttWrapper.getInstance().connect(
                        context,
                        url.host,
                        BuildConfig.MQTT_PORT,
                        pushOptions,
                        keepaliveTime,
                        settingsHelper.getDeviceId(),
                        nextRunnable,
                        nextRunnable
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    checkFactoryReset()
                }
            } else {
                try {
                    val serviceStartIntent = Intent(context, PushLongPollingService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context!!.startForegroundService(serviceStartIntent)
                    } else {
                        context!!.startService(serviceStartIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                checkFactoryReset()
            }
        } else {
            checkFactoryReset()
        }
    }

    private fun checkFactoryReset() {
        Log.d(Const.LOG_TAG, "checkFactoryReset() called")
        val config: ServerConfig? = if (settingsHelper != null) settingsHelper.getConfig() else null
        if (config != null && config.getFactoryReset() != null && config.getFactoryReset()) {
            // We got a factory reset request, let's confirm and erase everything!
            RemoteLogger.log(context, Const.LOG_INFO, "Device reset by server request")
            val confirmTask: ConfirmDeviceResetTask = object : ConfirmDeviceResetTask(context) {
                protected override fun onPostExecute(result: Int?) {
                    // Do a factory reset if we can
                    if (result == null || result !== Const.TASK_SUCCESS) {
                        RemoteLogger.log(
                            context,
                            Const.LOG_WARN,
                            "Failed to confirm device reset on server"
                        )
                    } else if (Utils.checkAdminMode(context)) {
                        // no_factory_reset restriction doesn't prevent against admin's reset action
                        // So we do not need to release this restriction prior to resetting the device
                        if (!Utils.factoryReset(context)) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Device reset failed")
                        }
                    } else {
                        RemoteLogger.log(
                            context,
                            Const.LOG_WARN,
                            "Device reset failed: no permissions"
                        )
                    }
                    // If we can't, proceed the initialization flow
                    checkRemoteReboot()
                }
            }

            val deviceInfo: DeviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true)
            deviceInfo.setFactoryReset(Utils.checkAdminMode(context))
            confirmTask.execute(deviceInfo)
        } else {
            checkRemoteReboot()
        }
    }

    private fun checkRemoteReboot() {
        val config: ServerConfig? = if (settingsHelper != null) settingsHelper.getConfig() else null
        if (config != null && config.getReboot() != null && config.getReboot()) {
            // Log and confirm reboot before rebooting
            RemoteLogger.log(context, Const.LOG_INFO, "Rebooting by server request")
            val confirmTask: ConfirmRebootTask = object : ConfirmRebootTask(context) {
                protected override fun onPostExecute(result: Int?) {
                    if (result == null || result !== Const.TASK_SUCCESS) {
                        RemoteLogger.log(
                            context,
                            Const.LOG_WARN,
                            "Failed to confirm reboot on server"
                        )
                    } else if (Utils.checkAdminMode(context)) {
                        if (!Utils.reboot(context)) {
                            RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed")
                        }
                    } else {
                        RemoteLogger.log(context, Const.LOG_WARN, "Reboot failed: no permissions")
                    }
                    checkPasswordReset()
                }
            }

            val deviceInfo: DeviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true)
            confirmTask.execute(deviceInfo)
        } else {
            checkPasswordReset()
        }
    }

    private fun checkPasswordReset() {
        val config: ServerConfig? = if (settingsHelper != null) settingsHelper.getConfig() else null
        if (config != null && config.getPasswordReset() != null) {
            if (Utils.passwordReset(context, config.getPasswordReset())) {
                RemoteLogger.log(context, Const.LOG_INFO, "Password successfully changed")
            } else {
                RemoteLogger.log(context, Const.LOG_WARN, "Failed to reset password")
            }

            val confirmTask: ConfirmPasswordResetTask = object : ConfirmPasswordResetTask(context) {
                protected override fun onPostExecute(result: Int?) {
                    setDefaultLauncher()
                }
            }

            val deviceInfo: DeviceInfo = DeviceInfoProvider.getDeviceInfo(context, true, true)
            confirmTask.execute(deviceInfo)
        } else {
            setDefaultLauncher()
        }
    }

    private fun setDefaultLauncher() {
        val config: ServerConfig? = if (settingsHelper != null) settingsHelper.getConfig() else null
        if (Utils.isDeviceOwner(context) && config != null) {
            // "Run default launcher" means we should not set Headwind MDM as a default launcher
            // and clear the setting if it has been already set
            val needSetLauncher =
                (config.getRunDefaultLauncher() == null || !config.getRunDefaultLauncher())
            val defaultLauncher: String = Utils.getDefaultLauncher(context)

            // As per the documentation, setting the default preferred activity should not be done on the main thread
            object : AsyncTask<Void?, Void?, Void?>() {
                override fun doInBackground(vararg voids: Void): Void? {
                    if (needSetLauncher && !context!!.packageName.equals(
                            defaultLauncher,
                            ignoreCase = true
                        )
                    ) {
                        Utils.setDefaultLauncher(context)
                    } else if (!needSetLauncher && context!!.packageName.equals(
                            defaultLauncher,
                            ignoreCase = true
                        )
                    ) {
                        Utils.clearDefaultLauncher(context)
                    }
                    return null
                }

                override fun onPostExecute(v: Void?) {
                    updatePolicies()
                }
            }.execute()
            return
        }
        updatePolicies()
    }

    private fun updatePolicies() {
        // Update miscellaneous device policies here

        // Set up a proxy server

        val settingsHelper: SettingsHelper = SettingsHelper.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && Utils.isDeviceOwner(context)) {
            var proxyUrl: String? = settingsHelper.getAppPreference(context!!.packageName, "proxy")
            if (proxyUrl != null) {
                proxyUrl = proxyUrl.trim { it <= ' ' }
                if (proxyUrl == "0") {
                    // null stays for "no changes" (most users won't even know about an option to set up a proxy)
                    // "0" stays for "clear the proxy previously set up"
                    proxyUrl = null
                }
                Utils.setProxy(context, proxyUrl)
            }
        }

        if (uiNotifier != null) {
            uiNotifier!!.onPoliciesUpdated()
        }
        Log.d(Const.LOG_TAG, "updatePolicies(): proceed to updating files")
        checkAndUpdateFiles()
    }

    private fun checkAndUpdateFiles() {
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg voids: Void): Void? {
                val config: ServerConfig = settingsHelper.getConfig()
                // This may be a long procedure due to checksum calculation so execute it in the background thread
                InstallUtils.generateFilesForInstallList(
                    context,
                    config.getFiles(),
                    filesForInstall
                )
                return null
            }

            override fun onPostExecute(v: Void?) {
                loadAndInstallFiles()
            }
        }.execute()
    }

    class RemoteFileStatus {
        var remoteFile: RemoteFile? = null
        var downloaded: Boolean = false
        var installed: Boolean = false
    }

    private fun loadAndInstallFiles() {
        val isGoodNetworkForUpdate = userInteraction || checkUpdateNetworkRestriction(
            settingsHelper.getConfig(),
            context!!
        )
        if (filesForInstall.size > 0 && !isGoodNetworkForUpdate) {
            RemoteLogger.log(
                context,
                Const.LOG_DEBUG,
                "Updating files not enabled: waiting for WiFi connection"
            )
        }
        if (filesForInstall.size > 0 && isGoodNetworkForUpdate) {
            val remoteFile: RemoteFile? = filesForInstall.removeAt(0)

            object : AsyncTask<RemoteFile?, Void?, RemoteFileStatus?>() {
                override fun doInBackground(vararg remoteFiles: RemoteFile): RemoteFileStatus? {
                    val remoteFile: RemoteFile = remoteFiles[0]
                    var remoteFileStatus: RemoteFileStatus? = null

                    if (remoteFile.isRemove()) {
                        RemoteLogger.log(
                            context,
                            Const.LOG_DEBUG,
                            "Removing file: " + remoteFile.getPath()
                        )
                        val file =
                            File(Environment.getExternalStorageDirectory(), remoteFile.getPath())
                        try {
                            if (file.exists()) {
                                file.delete()
                            }
                            RemoteFileTable.deleteByPath(
                                DatabaseHelper.instance(context).getWritableDatabase(),
                                remoteFile.getPath()
                            )
                        } catch (e: Exception) {
                            RemoteLogger.log(
                                context, Const.LOG_WARN, ("Failed to remove file: " +
                                        remoteFile.getPath()).toString() + ": " + e.message
                            )
                            e.printStackTrace()
                        }
                    } else if (remoteFile.getUrl() != null) {
                        if (uiNotifier != null) {
                            uiNotifier!!.onFileDownloading(remoteFile)
                        }

                        // onFileDownloading() method contents
                        // updateMessageForFileDownloading(remoteFile.getPath());
                        remoteFileStatus = RemoteFileStatus()
                        remoteFileStatus.remoteFile = remoteFile

                        val dbHelper: DatabaseHelper = DatabaseHelper.instance(context)
                        val lastDownload: Download = DownloadTable.selectByPath(
                            dbHelper.getReadableDatabase(),
                            remoteFile.getPath()
                        )
                        if (!canDownload(lastDownload, remoteFile.getPath())) {
                            // Do not make further attempts to download if there were earlier download or installation errors
                            return remoteFileStatus
                        }

                        var file: File? = null
                        try {
                            RemoteLogger.log(
                                context,
                                Const.LOG_DEBUG,
                                "Downloading file: " + remoteFile.getPath()
                            )
                            file = InstallUtils.downloadFile(context, remoteFile.getUrl(),
                                object : DownloadProgress() {
                                    override fun onDownloadProgress(
                                        progress: Int,
                                        total: Long,
                                        current: Long
                                    ) {
                                        if (uiNotifier != null) {
                                            uiNotifier!!.onDownloadProgress(
                                                progress,
                                                total,
                                                current
                                            )
                                        }
                                        // onDownloadProgress() method contents
                                        /*handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });*/
                                    }
                                })
                        } catch (e: Exception) {
                            RemoteLogger.log(
                                context, Const.LOG_WARN,
                                ("Failed to download file " + remoteFile.getPath()).toString() + ": " + e.message
                            )
                            e.printStackTrace()
                            // Save the download attempt in the database
                            saveFailedAttempt(
                                context,
                                lastDownload,
                                remoteFile.getUrl(),
                                remoteFile.getPath(),
                                false,
                                false
                            )
                        }

                        if (file != null) {
                            remoteFileStatus.downloaded = true
                            val finalFile = File(
                                Environment.getExternalStorageDirectory(),
                                remoteFile.getPath()
                            )
                            try {
                                if (finalFile.exists()) {
                                    finalFile.delete()
                                }
                                if (!remoteFile.isVarContent()) {
                                    FileUtils.moveFile(file, finalFile)
                                } else {
                                    var imei: String = DeviceInfoProvider.getImei(context, 0)
                                    if (imei == null || imei == "") {
                                        imei = settingsHelper.getConfig().getImei()
                                    }
                                    createFileFromTemplate(
                                        file,
                                        finalFile,
                                        settingsHelper.getDeviceId(),
                                        imei,
                                        settingsHelper.getConfig()
                                    )
                                }
                                RemoteFileTable.insert(dbHelper.getWritableDatabase(), remoteFile)
                                remoteFileStatus.installed = true
                                if (lastDownload != null) {
                                    DownloadTable.deleteByPath(
                                        dbHelper.getWritableDatabase(),
                                        lastDownload.getPath()
                                    )
                                }
                            } catch (e: Exception) {
                                RemoteLogger.log(
                                    context, Const.LOG_WARN,
                                    ("Failed to create file " + remoteFile.getPath()).toString() + ": " + e.message
                                )
                                e.printStackTrace()
                                // Remove initial file because we don't want to install this file any more
                                try {
                                    if (file.exists()) {
                                        file.delete()
                                    }
                                } catch (e1: Exception) {
                                    e1.printStackTrace()
                                }
                                remoteFileStatus.installed = false
                                // Save the install attempt in the database
                                saveFailedAttempt(
                                    context,
                                    lastDownload,
                                    remoteFile.getUrl(),
                                    remoteFile.getPath(),
                                    true,
                                    false
                                )
                            }
                        } else {
                            remoteFileStatus.downloaded = false
                            remoteFileStatus.installed = false
                        }
                    }

                    return remoteFileStatus
                }

                override fun onPostExecute(fileStatus: RemoteFileStatus?) {
                    if (fileStatus != null) {
                        if (!fileStatus.installed) {
                            filesForInstall.add(0, fileStatus.remoteFile)
                            if (uiNotifier != null) {
                                if (!fileStatus.downloaded) {
                                    uiNotifier!!.onFileDownloadError(fileStatus.remoteFile)
                                } else {
                                    uiNotifier!!.onFileInstallError(fileStatus.remoteFile)
                                }
                            }
                            // onFileDownloadError() method contents
                            /*
                            if (!ProUtils.kioskModeRequired(context)) {
                                // Notify the error dialog that we're downloading a file, not an app
                                downloadingFile = true;
                                createAndShowFileNotDownloadedDialog(fileStatus.remoteFile.getUrl());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                             */
                            return
                        }
                    }
                    Log.i(Const.LOG_TAG, "loadAndInstallFiles(): proceed to next file")
                    loadAndInstallFiles()
                }
            }.execute(remoteFile)
        } else {
            Log.i(Const.LOG_TAG, "loadAndInstallFiles(): Proceed to certificate installation")
            installCertificates()
        }
    }

    // Save failed attempt to download or install a file or an app in the database to avoid infinite loops
    private fun saveFailedAttempt(
        context: Context?,
        lastDownload: Download?,
        url: String,
        path: String,
        downloaded: Boolean,
        installed: Boolean
    ) {
        var lastDownload: Download? = lastDownload
        if (lastDownload == null) {
            lastDownload = Download()
            lastDownload.setUrl(url)
            lastDownload.setPath(path)
            lastDownload.setAttempts(0)
        }
        if (!downloaded) {
            lastDownload.setAttempts(lastDownload.getAttempts() + 1)
            lastDownload.setLastAttemptTime(System.currentTimeMillis())
        }
        lastDownload.setDownloaded(downloaded)
        lastDownload.setInstalled(installed)
        val dbHelper: DatabaseHelper = DatabaseHelper.instance(context)
        DownloadTable.insert(dbHelper.getWritableDatabase(), lastDownload)
    }

    // In background mode, we do not attempt to download files or apps in two cases:
    // 1. Installation failed
    // 2. Downloading in a mobile network is limited
    private fun canDownload(lastDownload: Download?, objectId: String): Boolean {
        if (userInteraction || lastDownload == null) {
            return true
        }
        if (lastDownload.isDownloaded() && !lastDownload.isInstalled()) {
            RemoteLogger.log(
                context, Const.LOG_INFO,
                "Skip download due to previous install failure: $objectId"
            )
            return false
        }
        val config: ServerConfig = SettingsHelper.getInstance(context).getConfig()
        if ("limited" == config.getDownloadUpdates()) {
            val cm = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            if (activeNetwork == null) {
                RemoteLogger.log(
                    context, Const.LOG_INFO,
                    "Skip downloading $objectId: no active network"
                )
                return false
            }
            Log.d(
                Const.LOG_TAG,
                "Active network; " + activeNetwork.typeName + ", download attempts: " + lastDownload.getAttempts()
            )
            if (activeNetwork.type == ConnectivityManager.TYPE_MOBILE &&
                !lastDownload.isDownloaded() && lastDownload.getAttempts() > 3
            ) {
                RemoteLogger.log(
                    context, Const.LOG_INFO,
                    "Skip download due to previous download failures: $objectId"
                )
                return false
            }
        }
        return true
    }

    private fun installCertificates() {
        val certPaths: String =
            settingsHelper.getAppPreference(context!!.packageName, "certificates")
        if (certPaths != null) {
            object : AsyncTask<Void?, Void?, Void?>() {
                override fun doInBackground(vararg voids: Void): Void? {
                    CertInstaller.installCertificatesFromFiles(
                        context,
                        certPaths.trim { it <= ' ' })
                    return null
                }

                override fun onPostExecute(v: Void?) {
                    checkAndUpdateApplications()
                }
            }.execute()
        } else {
            checkAndUpdateApplications()
        }
    }

    private fun checkAndUpdateApplications() {
        Log.i(Const.LOG_TAG, "checkAndUpdateApplications(): starting update applications")
        if (uiNotifier != null) {
            uiNotifier!!.onAppUpdateStart()
        }
        // onAppUpdateStart() method contents
        /*
        binding.setMessage( getString( R.string.main_activity_applications_update ) );
        configInitialized = true;
         */
        configInitializing = false

        val config: ServerConfig = settingsHelper.getConfig()
        InstallUtils.generateApplicationsForInstallList(
            context,
            config.getApplications(),
            applicationsForInstall,
            pendingInstallations
        )

        Log.i(
            Const.LOG_TAG,
            "checkAndUpdateApplications(): list size=" + applicationsForInstall.size
        )

        registerAppInstallReceiver(if (config != null) config.getAppPermissions() else null)
        loadAndInstallApplications()
    }

    private inner class ApplicationStatus {
        var application: Application? = null
        var installed: Boolean = false
    }

    // Here we avoid ConcurrentModificationException by executing all operations with applicationForInstall list in a main thread
    private fun loadAndInstallApplications() {
        val isGoodTimeForAppUpdate =
            userInteraction || checkAppUpdateTimeRestriction(settingsHelper.getConfig())
        if (applicationsForInstall.size > 0 && !isGoodTimeForAppUpdate) {
            RemoteLogger.log(
                context,
                Const.LOG_DEBUG,
                "Application update not enabled. Scheduled time: " + settingsHelper.getConfig()
                    .getAppUpdateFrom()
            )
        }
        val isGoodNetworkForUpdate = userInteraction || checkUpdateNetworkRestriction(
            settingsHelper.getConfig(),
            context!!
        )
        if (applicationsForInstall.size > 0 && !isGoodNetworkForUpdate) {
            RemoteLogger.log(
                context,
                Const.LOG_DEBUG,
                "Application update not enabled: waiting for WiFi connection"
            )
        }
        if (applicationsForInstall.size > 0 && isGoodTimeForAppUpdate && isGoodNetworkForUpdate) {
            val application: Application? = applicationsForInstall.removeAt(0)

            object : AsyncTask<Application?, Void?, ApplicationStatus?>() {
                override fun doInBackground(vararg applications: Application): ApplicationStatus? {
                    val application: Application = applications[0]
                    var applicationStatus: ApplicationStatus? = null

                    if (application.isRemove()) {
                        // Remove the app
                        RemoteLogger.log(
                            context,
                            Const.LOG_DEBUG,
                            "Removing app: " + application.getPkg()
                        )
                        if (uiNotifier != null) {
                            uiNotifier!!.onAppRemoving(application)
                        }
                        // onAppRemoving() method contents
                        //updateMessageForApplicationRemoving( application.getName() );
                        uninstallApplication(application.getPkg())
                    } else if (application.getUrl() == null) {
                        handler.post {
                            Log.i(
                                Const.LOG_TAG,
                                "loadAndInstallApplications(): proceed to next app"
                            )
                            loadAndInstallApplications()
                        }
                    } else if (application.getUrl().startsWith("market://details")) {
                        RemoteLogger.log(
                            context,
                            Const.LOG_INFO,
                            ("Installing app " + application.getPkg()).toString() + " from Google Play"
                        )
                        installApplicationFromPlayMarket(application.getUrl(), application.getPkg())
                        applicationStatus = ApplicationStatus()
                        applicationStatus.application = application
                        applicationStatus.installed = true
                    } else if (application.getUrl().startsWith("file:///")) {
                        RemoteLogger.log(
                            context,
                            Const.LOG_INFO,
                            ("Installing app " + application.getPkg()).toString() + " from SD card"
                        )
                        applicationStatus = ApplicationStatus()
                        applicationStatus.application = application
                        var file: File? = null
                        try {
                            Log.d(Const.LOG_TAG, "URL: " + application.getUrl())
                            file = File(URL(application.getUrl()).toURI())
                            if (file != null) {
                                Log.d(Const.LOG_TAG, "Path: " + file.absolutePath)
                                if (uiNotifier != null) {
                                    uiNotifier!!.onAppInstalling(application)
                                }
                                // onAppInstalling() method contents
                                //updateMessageForApplicationInstalling(application.getName());
                                installApplication(
                                    file,
                                    application.getPkg(),
                                    application.getVersion()
                                )
                                applicationStatus.installed = true
                            } else {
                                applicationStatus.installed = false
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            applicationStatus.installed = false
                        }
                    } else {
                        if (uiNotifier != null) {
                            uiNotifier!!.onAppDownloading(application)
                        }

                        // onAppDownloading() method contents
                        //updateMessageForApplicationDownloading(application.getName());
                        applicationStatus = ApplicationStatus()
                        applicationStatus.application = application

                        val dbHelper: DatabaseHelper = DatabaseHelper.instance(context)
                        val tempPath: String =
                            InstallUtils.getAppTempPath(context, application.getUrl())
                        val lastDownload: Download =
                            DownloadTable.selectByPath(dbHelper.getReadableDatabase(), tempPath)
                        if (!canDownload(lastDownload, application.getPkg())) {
                            // Do not make further attempts to download if there were earlier download or installation errors
                            applicationStatus.installed = false
                            return applicationStatus
                        }

                        var file: File? = null
                        try {
                            RemoteLogger.log(
                                context,
                                Const.LOG_DEBUG,
                                "Downloading app: " + application.getPkg()
                            )
                            file = InstallUtils.downloadFile(context, application.getUrl(),
                                object : DownloadProgress() {
                                    override fun onDownloadProgress(
                                        progress: Int,
                                        total: Long,
                                        current: Long
                                    ) {
                                        if (uiNotifier != null) {
                                            uiNotifier!!.onDownloadProgress(
                                                progress,
                                                total,
                                                current
                                            )
                                        }
                                        /*
                                            handler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    binding.progress.setMax(100);
                                                    binding.progress.setProgress(progress);

                                                    binding.setFileLength(total);
                                                    binding.setDownloadedLength(current);
                                                }
                                            });
                                             */
                                    }
                                })
                        } catch (e: Exception) {
                            RemoteLogger.log(
                                context,
                                Const.LOG_WARN,
                                ("Failed to download app " + application.getPkg()).toString() + ": " + e.message
                            )
                            e.printStackTrace()
                            // Save the download attempt in the database
                            saveFailedAttempt(
                                context,
                                lastDownload,
                                application.getUrl(),
                                tempPath,
                                false,
                                false
                            )
                        }

                        if (file != null) {
                            if (uiNotifier != null) {
                                uiNotifier!!.onAppInstalling(application)
                            }
                            // onAppInstalling() method contents
                            //updateMessageForApplicationInstalling(application.getName());
                            installApplication(file, application.getPkg(), application.getVersion())
                            applicationStatus.installed = true
                            // Here we remove app from pending downloads
                            // If it fails to install, we'll remember it and do not download any more
                            if (lastDownload != null) {
                                DownloadTable.deleteByPath(
                                    dbHelper.getWritableDatabase(),
                                    lastDownload.getPath()
                                )
                            }
                        } else {
                            applicationStatus.installed = false
                        }
                    }

                    return applicationStatus
                }

                override fun onPostExecute(applicationStatus: ApplicationStatus?) {
                    if (applicationStatus != null) {
                        if (applicationStatus.installed) {
                            if (applicationStatus.application.isRunAfterInstall()) {
                                applicationsForRun.add(applicationStatus.application)
                            }
                        } else {
                            applicationsForInstall.add(0, applicationStatus.application)
                            if (uiNotifier != null) {
                                uiNotifier!!.onAppDownloadError(applicationStatus.application)
                            }
                            // onAppDownloadError() method contents
                            /*
                            if (!ProUtils.kioskModeRequired(MainActivity.this)) {
                                // Notify the error dialog that we're downloading an app
                                downloadingFile = false;
                                createAndShowFileNotDownloadedDialog(applicationStatus.application.getName());
                                binding.setDownloading( false );
                            } else {
                                // Avoid user interaction in kiosk mode, just ignore download error and keep the old version
                                // Note: view is not used in this method so just pass null there
                                confirmDownloadFailureClicked(null);
                            }
                             */
                        }
                    }
                }
            }.execute(application)
        } else {
            // App install receiver is unregistered after all apps are installed or a timeout happens
            //unregisterAppInstallReceiver();
            lockRestrictions()
        }
    }

    private fun lockRestrictions() {
        if (settingsHelper.getConfig() != null && settingsHelper.getConfig()
                .getRestrictions() != null
        ) {
            Utils.lockUserRestrictions(context, settingsHelper.getConfig().getRestrictions())
        }
        notifyThreads()
    }

    private fun notifyThreads() {
        val config: ServerConfig = settingsHelper.getConfig()
        if (config != null) {
            val intent: Intent = Intent(Const.ACTION_TOGGLE_PERMISSIVE)
            intent.putExtra(Const.EXTRA_ENABLED, config.isPermissive() || config.isKioskMode())
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
        }
        setActions()
    }

    private fun setActions() {
        val config: ServerConfig = settingsHelper.getConfig()
        // As per the documentation, setting the default preferred activity should not be done on the main thread
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg voids: Void): Void? {
                // If kiosk browser is installed, make it a default browser
                // This is a temporary solution! Perhaps user wants only to open specific hosts / schemes
                if (Utils.isDeviceOwner(context)) {
                    if (config.getActions() != null && config.getActions().size() > 0) {
                        for (action in config.getActions()) {
                            Utils.setAction(context, action)
                        }
                    }
                }
                return null
            }

            override fun onPostExecute(v: Void?) {
                if (uiNotifier != null) {
                    uiNotifier!!.onConfigUpdateComplete()
                }

                // Send notification about the configuration update to all plugins
                val intent: Intent =
                    Intent(Const.INTENT_PUSH_NOTIFICATION_PREFIX + PushMessage.TYPE_CONFIG_UPDATED)
                context!!.sendBroadcast(intent)

                RemoteLogger.log(context, Const.LOG_VERBOSE, "Update flow completed")
                if (pendingInstallations.size > 0) {
                    // Some apps are still pending installation
                    // Let's wait until they're all installed
                    // Then notify UI about that so it could refresh the screen
                    waitForInstallComplete()
                } else {
                    unregisterAppInstallReceiver()
                }

                // onConfigUpdateComplete() method contents
                /*
                Log.i(Const.LOG_TAG, "Showing content from setActions()");
                showContent(settingsHelper.getConfig());
                 */
            }
        }.execute()
    }

    private fun waitForInstallComplete() {
        object : AsyncTask<Void?, Void?, Void?>() {
            override fun doInBackground(vararg voids: Void): Void? {
                for (n in 0..59) {
                    if (pendingInstallations.size == 0) {
                        break
                    }
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                unregisterAppInstallReceiver()
                if (uiNotifier != null) {
                    uiNotifier!!.onAllAppInstallComplete()
                }
                return null
            }
        }.execute()
    }


    @SuppressLint("WrongConstant,UnspecifiedRegisterReceiverFlag")
    private fun registerAppInstallReceiver(appPermissionStrategy: String?) {
        // Here we handle the completion of the silent app installation in the device owner mode
        // These intents are not delivered to LocalBroadcastManager
        if (appInstallReceiver == null) {
            Log.d(Const.LOG_TAG, "Install completion receiver prepared")
            appInstallReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Const.ACTION_INSTALL_COMPLETE) {
                        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
                        when (status) {
                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                RemoteLogger.log(
                                    context,
                                    Const.LOG_INFO,
                                    "Request user confirmation to install"
                                )
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
                                val packageName = intent.getStringExtra(Const.PACKAGE_NAME)
                                if (packageName != null) {
                                    RemoteLogger.log(
                                        context, Const.LOG_DEBUG,
                                        "App $packageName installed successfully"
                                    )
                                    Log.i(
                                        Const.LOG_TAG,
                                        "Install complete: $packageName"
                                    )
                                    val file = pendingInstallations[packageName]
                                    if (file != null) {
                                        pendingInstallations.remove(packageName)
                                        InstallUtils.deleteTempApk(file)
                                    }
                                    if (BuildConfig.SYSTEM_PRIVILEGES || Utils.isDeviceOwner(context)) {
                                        // Always grant all dangerous rights to the app
                                        Utils.autoGrantRequestedPermissions(
                                            context, packageName,
                                            appPermissionStrategy, false
                                        )
                                        if (BuildConfig.SYSTEM_PRIVILEGES && packageName == Const.APUPPET_PACKAGE_NAME) {
                                            // Automatically grant required permissions to aPuppet if we can
                                            // Note: device owner can only grant permissions to self, not to other apps!
                                            try {
                                                SystemUtils.autoSetAccessibilityPermission(
                                                    context,
                                                    Const.APUPPET_PACKAGE_NAME,
                                                    Const.APUPPET_SERVICE_CLASS_NAME
                                                )
                                                SystemUtils.autoSetOverlayPermission(
                                                    context,
                                                    Const.APUPPET_PACKAGE_NAME
                                                )
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    if (uiNotifier != null) {
                                        uiNotifier!!.onAppInstallComplete(packageName)
                                    }
                                } else {
                                    RemoteLogger.log(
                                        context,
                                        Const.LOG_DEBUG,
                                        "App installed successfully"
                                    )
                                }
                            }

                            else -> {
                                // Installation failure
                                val extraMessage =
                                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                                val statusMessage: String =
                                    InstallUtils.getPackageInstallerStatusMessage(status)
                                packageName = intent.getStringExtra(Const.PACKAGE_NAME)
                                var logRecord = "Install failed: $statusMessage"
                                if (packageName != null) {
                                    logRecord = packageName + " " + logRecord
                                }
                                if (extraMessage != null && extraMessage.length > 0) {
                                    logRecord += ", extra: $extraMessage"
                                }
                                RemoteLogger.log(context, Const.LOG_ERROR, logRecord)
                                if (packageName != null) {
                                    val file = pendingInstallations[packageName]
                                    if (file != null) {
                                        pendingInstallations.remove(packageName)
                                        InstallUtils.deleteTempApk(file)
                                        // Save failed install attempt to prevent next downloads
                                        saveFailedAttempt(
                                            context,
                                            null,
                                            "",
                                            file.absolutePath,
                                            true,
                                            false
                                        )
                                    }
                                }
                            }
                        }
                        loadAndInstallApplications()
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

    private fun installApplicationFromPlayMarket(uri: String, packageName: String) {
        RemoteLogger.log(context, Const.LOG_DEBUG, "Asking user to install app $packageName")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(Uri.parse(uri))
        try {
            context!!.startActivity(intent)
        } catch (e: Exception) {
            RemoteLogger.log(
                context, Const.LOG_DEBUG,
                "Failed to run app install activity for $packageName"
            )
        }
    }

    // This function is called from a background thread
    private fun installApplication(file: File, packageName: String, version: String?) {
        if (packageName == context!!.packageName &&
            context!!.packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID) != null
        ) {
            // Restart self in EMUI: there's no auto restart after update in EMUI, we must use a helper app
            startLauncherRestarter()
        }
        val versionData = if (version == null || version == "0") "" else " $version"
        if (Utils.isDeviceOwner(context) || BuildConfig.SYSTEM_PRIVILEGES) {
            pendingInstallations[packageName] = file
            RemoteLogger.log(
                context, Const.LOG_INFO,
                "Silently installing app $packageName$versionData"
            )
            InstallUtils.silentInstallApplication(
                context,
                file,
                packageName,
                object : InstallErrorHandler() {
                    override fun onInstallError(msg: String?) {
                        Log.i(
                            Const.LOG_TAG,
                            "installApplication(): error installing app $packageName"
                        )
                        pendingInstallations.remove(packageName)
                        if (file.exists()) {
                            file.delete()
                        }
                        if (uiNotifier != null) {
                            uiNotifier!!.onAppInstallError(packageName)
                        }
                        if (msg != null) {
                            RemoteLogger.log(
                                context, Const.LOG_WARN,
                                "Failed to install app $packageName: $msg"
                            )
                        }
                        // Save failed install attempt to prevent next downloads
                        saveFailedAttempt(context, null, "", file.absolutePath, true, false)
                        /*
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setMessage(getString(R.string.install_error) + " " + packageName)
                                    .setPositiveButton(R.string.dialog_administrator_mode_continue, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            checkAndStartLauncher();
                                        }
                                    })
                                    .create()
                                    .show();
                        }
                    });
                     */
                    }
                })
        } else {
            RemoteLogger.log(
                context, Const.LOG_INFO,
                "Asking user to install app $packageName$versionData"
            )
            InstallUtils.requestInstallApplication(context, file, object : InstallErrorHandler() {
                override fun onInstallError(msg: String?) {
                    pendingInstallations.remove(packageName)
                    if (file.exists()) {
                        file.delete()
                    }
                    if (msg != null) {
                        RemoteLogger.log(
                            context, Const.LOG_WARN,
                            "Failed to install app $packageName: $msg"
                        )
                    }
                    // Save failed install attempt to prevent next downloads
                    saveFailedAttempt(context, null, "", file.absolutePath, true, false)
                    handler.post { loadAndInstallApplications() }
                }
            })
        }
    }

    private fun uninstallApplication(packageName: String) {
        if (Utils.isDeviceOwner(context) || BuildConfig.SYSTEM_PRIVILEGES) {
            RemoteLogger.log(context, Const.LOG_INFO, "Silently uninstall app $packageName")
            InstallUtils.silentUninstallApplication(context, packageName)
        } else {
            RemoteLogger.log(context, Const.LOG_INFO, "Asking user to uninstall app $packageName")
            InstallUtils.requestUninstallApplication(context, packageName)
        }
    }

    // The following algorithm of launcher restart works in EMUI:
    // Run EMUI_LAUNCHER_RESTARTER activity once and send the old version number to it.
    // The restarter application will check the launcher version each second, and restart it
    // when it is changed.
    private fun startLauncherRestarter() {
        // Sending an intent before updating, otherwise the launcher may be terminated at any time
        val intent =
            context!!.packageManager.getLaunchIntentForPackage(Const.LAUNCHER_RESTARTER_PACKAGE_ID)
        if (intent == null) {
            Log.i("LauncherRestarter", "No restarter app, please add it in the config!")
            return
        }
        intent.putExtra(Const.LAUNCHER_RESTARTER_OLD_VERSION, BuildConfig.VERSION_NAME)
        context!!.startActivity(intent)
        Log.i("LauncherRestarter", "Calling launcher restarter from the launcher")
    }

    // Create a new file from the template file
    // (replace DEVICE_NUMBER, IMEI, CUSTOM* by their values)
    @Throws(IOException::class)
    private fun createFileFromTemplate(
        srcFile: File,
        dstFile: File,
        deviceId: String,
        imei: String?,
        config: ServerConfig
    ) {
        // We are supposed to process only small text files
        // So here we are reading the whole file, replacing variables, and save the content
        // It is not optimal for large files - it would be better to replace in a stream (how?)
        var content: String = FileUtils.readFileToString(srcFile)
        content = content.replace("DEVICE_NUMBER", deviceId)
            .replace("IMEI", imei ?: "")
            .replace("CUSTOM1", if (config.getCustom1() != null) config.getCustom1() else "")
            .replace("CUSTOM2", if (config.getCustom2() != null) config.getCustom2() else "")
            .replace("CUSTOM3", if (config.getCustom3() != null) config.getCustom3() else "")
        FileUtils.writeStringToFile(dstFile, content)
    }

    val isPendingAppInstall: Boolean
        get() = applicationsForInstall.size > 0

    fun repeatDownloadFiles() {
        loadAndInstallFiles()
    }

    fun repeatDownloadApps() {
        loadAndInstallApplications()
    }

    fun skipDownloadFiles() {
        Log.d(Const.LOG_TAG, "File download skipped, continue updating files")
        if (filesForInstall.size > 0) {
            val remoteFile: RemoteFile? = filesForInstall.removeAt(0)
            settingsHelper.removeRemoteFile(remoteFile)
        }
        loadAndInstallFiles()
    }

    fun skipDownloadApps() {
        Log.d(Const.LOG_TAG, "App download skipped, continue updating applications")
        if (applicationsForInstall.size > 0) {
            val application: Application? = applicationsForInstall.removeAt(0)
            // Mark this app not to download any more until the config is refreshed
            // But we should not remove the app from a list because it may be
            // already installed!
            settingsHelper.removeApplicationUrl(application)
        }
        loadAndInstallApplications()
    }

    companion object {
        fun notifyConfigUpdate(context: Context) {
            if (SettingsHelper.getInstance(context).isMainActivityRunning()) {
                Log.d(Const.LOG_TAG, "Main activity is running, using activity updater")
                LocalBroadcastManager.getInstance(context).sendBroadcast
                (Intent(Const.ACTION_UPDATE_CONFIGURATION))
            } else {
                Log.d(Const.LOG_TAG, "Main activity is not running, creating a new ConfigUpdater")
                ConfigUpdater().updateConfig(context, null, false)
            }
        }

        @JvmOverloads
        fun forceConfigUpdate(
            context: Context,
            notifier: UINotifier? = null,
            userInteraction: Boolean = false
        ) {
            ConfigUpdater().updateConfig(context, notifier, userInteraction)
        }

        fun checkUpdateNetworkRestriction(config: ServerConfig, context: Context): Boolean {
            if ("wifi" != config.getDownloadUpdates()) {
                return true
            }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            return activeNetwork != null && activeNetwork.type != ConnectivityManager.TYPE_MOBILE
        }

        fun checkAppUpdateTimeRestriction(config: ServerConfig): Boolean {
            if (config.getAppUpdateFrom() == null || config.getAppUpdateTo() == null) {
                return true
            }

            val date = Date()
            val calendar = GregorianCalendar.getInstance()
            calendar.time = date
            val hour = calendar[Calendar.HOUR_OF_DAY]
            var minute = calendar[Calendar.MINUTE]

            var appUpdateFromHour = 0
            try {
                appUpdateFromHour = config.getAppUpdateFrom().substring(0, 2).toInt()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            var appUpdateFromMinute = 0
            try {
                appUpdateFromMinute = config.getAppUpdateFrom().substring(3).toInt()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var appUpdateToHour = 0
            try {
                appUpdateToHour = config.getAppUpdateTo().substring(0, 2).toInt()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            var appUpdateToMinute = 0
            try {
                appUpdateToMinute = config.getAppUpdateTo().substring(3).toInt()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            minute += 60 * hour
            appUpdateFromMinute += 60 * appUpdateFromHour
            appUpdateToMinute += 60 * appUpdateToHour

            if (appUpdateFromMinute == appUpdateToMinute) {
                // This is incorrect. Perhaps the admin meant "24 hours" so return true
                return true
            }

            if (appUpdateFromMinute < appUpdateToMinute) {
                // Midnight not included
                return appUpdateFromMinute <= minute && minute <= appUpdateToMinute
            }

            // Midnight included
            return minute >= appUpdateFromMinute || minute <= appUpdateToMinute
        }
    }
}