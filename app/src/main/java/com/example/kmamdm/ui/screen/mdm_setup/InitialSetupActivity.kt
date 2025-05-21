package com.example.kmamdm.ui.screen.mdm_setup

import android.app.AlertDialog
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ActivityInitialSetupBinding
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.helper.Initializer
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.Application
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.ui.screen.base.BaseActivity
import com.example.kmamdm.ui.screen.error_details.ErrorDetailsActivity
import com.example.kmamdm.ui.screen.main.MainViewModel
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InitialSetupActivity : BaseActivity<MainViewModel, ActivityInitialSetupBinding>(), ConfigUpdater.UINotifier {
    private var configUpdater: ConfigUpdater? = null
    private var settingsHelper: SettingsHelper? = null
    private var configuring = false

    override fun createViewModel(): Class<MainViewModel> = MainViewModel::class.java

    override fun getContentView(): Int = R.layout.activity_initial_setup

    override fun initView() {
        Log.d(Const.LOG_TAG, "Launching the initial setup activity")

        // Here we must already get the Device Owner permissions
        // So install the required certificates
        // CertInstaller.installCertificatesFromAssets(this)

        mDataBinding.tvMessage.text = getString(R.string.initializing_mdm)
        mDataBinding.pbLoading.visibility = android.view.View.VISIBLE
    }

    override fun bindViewModel() {

    }

    protected override fun onResume() {
        super.onResume()

        settingsHelper = SettingsHelper.getInstance(this)
        settingsHelper?.setAppStartTime(System.currentTimeMillis())

        if (!configuring) {
            configuring = true
            configUpdater = ConfigUpdater()
            configUpdater?.setLoadOnly(true)
            updateConfig()
        }
    }

    private fun updateConfig() {
        configUpdater?.updateConfig(this, this, true)
    }

    override fun onConfigUpdateStart() {
        Log.d(Const.LOG_TAG, "Initial setup activity: onConfigUpdateStart")
    }

    override fun onConfigUpdateServerError(errorText: String?) {
        Log.d(Const.LOG_TAG, "Initial setup activity: onConfigUpdateServerError")
        displayError(
            getString(
                R.string.dialog_server_error_title), errorText ?: "No error text"
        )
    }

    override fun onConfigUpdateNetworkError(errorText: String?) {
        Log.d(Const.LOG_TAG, "Initial setup activity: onConfigUpdateNetworkError")
        displayError(
            getString(
                R.string.dialog_network_error_title,
                "your server"
            ), errorText ?: "No error text"
        )
    }

    override fun onConfigLoaded() {
        // Set Headwind MDM as the default launcher if required
        val config: ServerConfig? = settingsHelper?.getConfig()
        if (config != null) {
            // Device owner should be already granted, so we grant requested permissions early
            val deviceOwner: Boolean = Utils.isDeviceOwner(this)
            Log.d(Const.LOG_TAG, "Device Owner: $deviceOwner")
            getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE).edit().putInt(
                Const.PREFERENCES_DEVICE_OWNER,
                if (deviceOwner) Const.PREFERENCES_ON else Const.PREFERENCES_OFF
            ).commit()
            if (deviceOwner) {
                Utils.autoGrantRequestedPermissions(
                    this,
                    packageName,
                    null,
                    true
                )
            }

            if (Utils.isDeviceOwner(this) &&
                (config.runDefaultLauncher == null || !config.runDefaultLauncher)
            ) {
                lifecycleScope.launch(Dispatchers.IO) {
                    Utils.setDefaultLauncher(this@InitialSetupActivity)
                    withContext(Dispatchers.Main) {
                        completeConfig(settingsHelper)
                    }
                }
                return
            } else {
                // Headwind MDM works with default system launcher
                // Run services here
                // TODO: permissions required for watchdog services are not yet granted
                // so watchdog services are not being started at this point.
                // Perhaps we need to request these permissions at this step?
                Log.d(Const.LOG_TAG, "Working in background, starting services and installing apps")
                Initializer.init(this@InitialSetupActivity)
                // Initializer.startServicesAndLoadConfig(this@InitialSetupActivity)
            }
        }
        completeConfig(settingsHelper)
    }

    private fun completeConfig(settingsHelper: SettingsHelper?) {
        configuring = false
        if (settingsHelper?.getConfig() != null) {
            try {
                Initializer.applyEarlyNonInteractivePolicies(this, settingsHelper.getConfig()!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        settingsHelper?.setIntegratedProvisioningFlow(true)

        Log.d(Const.LOG_TAG, "Initial setup activity: setup completed")
        setResult(RESULT_OK)
        finish()
    }

    private fun displayError(message: String, detailsText: String) {
        try {
            AlertDialog.Builder(this)
                .setMessage(message)
                .setNeutralButton(R.string.main_activity_details) { dialogInterface, i ->
                    details(
                        detailsText
                    )
                }
                .setNegativeButton(R.string.main_activity_wifi) { dialogInterface, i -> openWiFiSettings() }
                .setPositiveButton(R.string.main_activity_repeat) { dialogInterface, i -> updateConfig() }
                .create()
                .show()
        } catch (e: Exception) {
            // Fatal Exception: android.view.WindowManager$BadTokenException
            // Unable to add window -- token android.os.BinderProxy@4a95f1c is not valid; is your activity running?
            // Shouldn't we reset the device here to avoid hanging up?
            e.printStackTrace()
        }
    }

    private fun details(detailsText: String) {
        configuring = false
        ErrorDetailsActivity.display(this, detailsText, true)
    }

    private fun openWiFiSettings() {
        configuring = false
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun abort() {
        // Factory reset!!!
        if (!Utils.factoryReset(this)) {
            Log.d(Const.LOG_WARN.toString(), "Device reset failed")
        }
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onPoliciesUpdated() {
        // Not used in early setup
    }

    override fun onDownloadProgress(progress: Int, total: Long, current: Long) {
        // Not used in early setup
    }

    override fun onAppUpdateStart() {
        // Not used in early setup
    }

    override fun onAppDownloadError(application: Application?) {
        // Not used in early setup
    }

    override fun onAppInstallError(packageName: String?) {
        // Not used in early setup
    }

    override fun onAppInstallComplete(packageName: String?) {
        // Not used in early setup
    }

    override fun onAppRemoving(application: Application) {

    }

    override fun onAppDownloading(application: Application) {

    }

    override fun onAppInstalling(application: Application) {

    }

    override fun onConfigUpdateComplete() {
        // Not used in early setup
    }

    override fun onAllAppInstallComplete() {
        // Not used in early setup
    }
}