package com.example.kmamdm.helper

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.kmamdm.model.Application
import com.example.kmamdm.server.json.ServerConfigResponse
import com.example.kmamdm.server.repository.ConfigurationRepository
import com.example.kmamdm.utils.Const
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
//        fun onFileDownloading(remoteFile: RemoteFile?)
//        fun onDownloadProgress(progress: Int, total: Long, current: Long)
//        fun onFileDownloadError(remoteFile: RemoteFile?)
//        fun onFileInstallError(remoteFile: RemoteFile?)
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
    private var userInteraction = false

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
        CoroutineScope(Dispatchers.IO).launch {
            ConfigurationRepository.getServerConfig(object : Callback<ServerConfigResponse> {
                override fun onResponse(
                    call: Call<ServerConfigResponse>,
                    response: Response<ServerConfigResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        // save config to share pref
                        SettingsHelper.getInstance(context).updateConfig(response.body()!!.data)
                        uiNotifier?.onConfigUpdateComplete()
                    }
                }

                override fun onFailure(call: Call<ServerConfigResponse>, t: Throwable) {
                    Toast.makeText(context, "Get failed", Toast.LENGTH_SHORT).show()
                    uiNotifier?.onConfigUpdateServerError(t.message)
                }
            })
        }
    }
}