package com.example.kmamdm.ui.screen.main

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ActivityMainBinding
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.helper.ConfigUpdater.UINotifier
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.Application
import com.example.kmamdm.model.ServerConfig
import com.example.kmamdm.pro.KioskUtils
import com.example.kmamdm.service.FloatingButtonService
import com.example.kmamdm.service.FloatingButtonService.LocalBinder
import com.example.kmamdm.ui.adapter.BaseAppListAdapter
import com.example.kmamdm.ui.adapter.MainAppListAdapter
import com.example.kmamdm.ui.dialog.AdministratorModeDialog
import com.example.kmamdm.ui.dialog.DownloadAndInstallAppDialog
import com.example.kmamdm.ui.dialog.EnterDeviceIdDialog
import com.example.kmamdm.ui.dialog.ManageStorageDialog
import com.example.kmamdm.ui.dialog.NetworkErrorDialog
import com.example.kmamdm.ui.dialog.OverlaySettingsDialog
import com.example.kmamdm.ui.dialog.UnknownSourcesDialog
import com.example.kmamdm.ui.screen.adminmoderequest.AdminModeRequestActivity
import com.example.kmamdm.ui.screen.base.BaseActivity
import com.example.kmamdm.utils.AppInfo
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess


class MainActivity : BaseActivity<MainViewModel, ActivityMainBinding>(), View.OnClickListener,
    OnLongClickListener, UINotifier, BaseAppListAdapter.OnAppChooseListener,
    BaseAppListAdapter.SwitchAdapterListener {

    private lateinit var preferences: SharedPreferences

    private var exitView: ImageView? = null
    private var infoView: ImageView? = null
    private var updateView: ImageView? = null
    private var mainAppListAdapter: MainAppListAdapter? = null

    private val configUpdater = ConfigUpdater()
    private var needRedrawContentAfterReconfigure = false
    private var orientationLocked = false
    private var firstStartAfterProvisioning = false

    private var downloadAndInstallAppDialog: DownloadAndInstallAppDialog? = null

    private val handler = Handler()

    private var kioskUnlockCounter = 0
    private var configFault = false

    private var floatingButtonService: FloatingButtonService? = null
    private var isBound = false

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            floatingButtonService = binder.service
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
        }
    }

    private fun startAndBindService(listener: View.OnClickListener) {
        if (!isBound) {
            val intent = Intent(this, FloatingButtonService::class.java)
            startService(intent)
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        } else {
            floatingButtonService?.showFloatingButton()
        }
        setFloatingButtonOnClickListener(listener)
    }

    private fun setFloatingButtonOnClickListener(listener: View.OnClickListener, attempts: Int = 0) {
        handler.postDelayed({
            if (isBound) {
                floatingButtonService?.setFloatingButtonOnClickListener(listener)
            } else {
                if (attempts >= 6) {
                    Log.e(Const.LOG_TAG, "Failed to bind FloatingButtonService after 5 attempts")
                    return@postDelayed
                }
                setFloatingButtonOnClickListener(listener, attempts + 1)
            }
        }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun createViewModel() = MainViewModel::class.java

    override fun getContentView(): Int = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(
            Const.LOG_TAG,
            "MainActivity started" + (if (intent != null && intent.action != null) ", action: " + intent.action else "")
        )
        if (intent != null && "android.app.action.PROVISIONING_SUCCESSFUL".equals(
                intent.action,
                ignoreCase = true
            )
        ) {
            firstStartAfterProvisioning = true
        }
    }

    override fun initView() {
        preferences = getSharedPreferences(Const.PREFERENCES, MODE_PRIVATE)
    }

    override fun bindViewModel() {

    }

    override fun onResume() {
        super.onResume()
        if (interruptResumeFlow) {
            interruptResumeFlow = false
            return
        }

        if (firstStartAfterProvisioning) {
            firstStartAfterProvisioning = false
            waitForProvisioning(10)
        } else {
            setDefaultLauncherEarly()
        }
    }

    private fun waitForProvisioning(attempts: Int) {
        if (Utils.isDeviceOwner(this) || attempts <= 0) {
            setDefaultLauncherEarly()
        } else {
            handler.postDelayed({ waitForProvisioning(attempts - 1) }, 1000)
        }
    }

    private fun setDefaultLauncherEarly() {
        val config: ServerConfig? = SettingsHelper.getInstance(this).getConfig()
        if (config == null && Utils.isDeviceOwner(this)) {
            // At first start, temporarily set Headwind MDM as a default launcher
            // to prevent the user from clicking Home to stop running Headwind MDM
            val defaultLauncher = Utils.getDefaultLauncher(this)
            CoroutineScope(Dispatchers.IO).launch {
                if (!packageName.equals(defaultLauncher, ignoreCase = true)) {
                    Utils.setDefaultLauncher(this@MainActivity)
                }
                withContext(Dispatchers.Main) {
                    checkAndStartLauncher()
                }
            }
            return
        }
        checkAndStartLauncher()
    }

    private fun checkAndStartLauncher() {
        val unknownSourceMode = preferences.getInt(Const.PREFERENCES_UNKNOWN_SOURCES, -1)
        if (unknownSourceMode == -1) {
            if (checkUnknownSources()) {
                preferences.edit().putInt(Const.PREFERENCES_UNKNOWN_SOURCES, Const.PREFERENCES_ON)
                    .commit()
            } else {
                return
            }
        }

        val administratorMode = preferences.getInt(Const.PREFERENCES_ADMINISTRATOR, -1)
        if (administratorMode == -1) {
            if (checkAdminMode()) {
                preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_ON)
                    .commit()
            } else {
                return
            }
        }

        val overlayMode = preferences.getInt(Const.PREFERENCES_OVERLAY, -1)
        if (overlayMode == -1) {
            if (checkAlarmWindow()) {
                preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_ON).commit()
            } else {
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manageStorageMode = preferences.getInt(Const.PREFERENCES_MANAGE_STORAGE, -1)
            if (manageStorageMode == -1) {
                if (checkManageStorage()) {
                    preferences.edit()
                        .putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_ON).commit()
                } else {
                    return
                }
            }
        }

        startLauncher()
    }

    private fun checkUnknownSources(): Boolean {
        if (!Utils.canInstallPackages(this)) {
            val unknownSourcesDialog = UnknownSourcesDialog {
                continueUnknownSources()
            }
            unknownSourcesDialog.show(supportFragmentManager, "unknown_sources")
            return false
        } else {
            return true
        }
    }

    fun continueUnknownSources() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } else {
            // In Android Oreo and above, permission to install packages are set per each app
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse(
                        "package:$packageName"
                    )
                )
            )
        }
    }

    private fun checkAdminMode(): Boolean {
        if (!Utils.checkAdminMode(this)) {
            val administratorModeDialog = AdministratorModeDialog(
                onSkip = {
                    skipAdminMode()
                },
                onContinue = {
                    setAdminMode()
                }
            )
            administratorModeDialog.show(supportFragmentManager, "administrator_mode")
            return false
        }
        return true
    }

    private fun skipAdminMode() {
        preferences.edit().putInt(Const.PREFERENCES_ADMINISTRATOR, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    private fun setAdminMode() {
        startActivity(Intent(this, AdminModeRequestActivity::class.java))
    }

    private fun checkAlarmWindow(): Boolean {
        if (!Utils.canDrawOverlays(this)) {
            val overlaySettingsDialog = OverlaySettingsDialog(
                onSkip = {
                    overlayWithoutPermission()
                },
                onContinue = {
                    continueOverlay()
                }
            )
            overlaySettingsDialog.show(supportFragmentManager, "overlay_settings")
            return false
        } else {
            return true
        }
    }

    private fun overlayWithoutPermission() {
        preferences.edit().putInt(Const.PREFERENCES_OVERLAY, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    private fun continueOverlay() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivityForResult(intent, 1001)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.overlays_not_supported, Toast.LENGTH_LONG).show()
            overlayWithoutPermission()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun checkManageStorage(): Boolean {
        if (!Environment.isExternalStorageManager()) {
            val manageStorageDialog = ManageStorageDialog(
                onSkip = {
                    storageWithoutPermission()
                },
                onContinue = {
                    continueStorage()
                }
            )
            manageStorageDialog.show(supportFragmentManager, "manage_storage")
            return false
        }
        return true
    }

    private fun storageWithoutPermission() {
        preferences.edit().putInt(Const.PREFERENCES_MANAGE_STORAGE, Const.PREFERENCES_OFF).commit()
        checkAndStartLauncher()
    }

    private fun continueStorage() {
        try {
            val intent = Intent()
            intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            val uri = Uri.fromParts("package", this.packageName, null)
            intent.setData(uri)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
            val intent = Intent()
            intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivity(intent)
        }
    }

    private fun startLauncher() {
        if (!configInitialized) {
            Log.i(Const.LOG_TAG, "Updating configuration in startLauncher()")
            updateConfig()
        } else {
            showContent()
        }
    }

    private fun createButtons() {
        val config = SettingsHelper.getInstance(this).getConfig()
        if (config?.kioskMode == true && config.kioskApps.isNotEmpty()) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this, getString(
                        R.string.kiosk_mode_requires_overlays,
                        getString(R.string.white_app_name)
                    ), Toast.LENGTH_LONG
                ).show()
                config.kioskMode = false
                SettingsHelper.getInstance(this).updateConfig(config)
                createLauncherButtons()
                return
            }
            startAndBindService {
                kioskUnlockCounter++
                if (kioskUnlockCounter >= Const.KIOSK_UNLOCK_CLICK_COUNT) {
                    floatingButtonService?.closeFloatingButton()
                    val restoreLauncherIntent = Intent(
                        this@MainActivity,
                        MainActivity::class.java
                    )
                    interruptResumeFlow = true
                    restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(restoreLauncherIntent)
                    // createAndShowEnterPasswordDialog()
                    if (KioskUtils.isKioskModeRunning(this)) {
                        KioskUtils.unlockKiosk(this)
                    }
                    kioskUnlockCounter = 0
                }
            }
        } else {
            createLauncherButtons()
        }
    }

    private fun createLauncherButtons() {
        createExitButton()
        createInfoButton()
        createUpdateButton()
    }

    private fun createManageButton(
        imageResource: Int,
        imageResourceBlack: Int,
        offset: Int
    ): ImageView {
        val layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        layoutParams.addRule(RelativeLayout.CENTER_VERTICAL)
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)

        val view = RelativeLayout(this)
        // Offset is multiplied by 2 because the view is centered. Yeah I know its an Induism)
        view.setPadding(0, offset * 2, 10, 0)
        view.layoutParams = layoutParams

        val manageButton = ImageView(this)
        manageButton.setImageResource(imageResource)
        view.addView(manageButton)

        try {
            val root: RelativeLayout = findViewById(R.id.activity_main)
            root.addView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return manageButton
    }

    private fun createExitButton() {
        if (exitView != null) {
            return
        }
        exitView = createManageButton(
            R.drawable.ic_vpn_key_opaque_24dp,
            R.drawable.ic_vpn_key_black_24dp,
            0
        )
        exitView?.setOnLongClickListener(this)
    }

    private fun createInfoButton() {
        if (infoView != null) {
            return
        }
        infoView = createManageButton(
            R.drawable.ic_info_opaque_24dp, R.drawable.ic_info_black_24dp,
            resources.getDimensionPixelOffset(R.dimen.info_icon_margin)
        )
        infoView?.setOnClickListener(this)
    }

    private fun createUpdateButton() {
        if (updateView != null) {
            return
        }
        updateView = createManageButton(
            R.drawable.ic_system_update_opaque_24dp, R.drawable.ic_system_update_black_24dp,
            (2.05f * resources.getDimensionPixelOffset(R.dimen.info_icon_margin)).toInt()
        )
        updateView?.setOnClickListener(this)
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {

    }

    override fun onClick(v: View?) {
        if (v == infoView) {
            // createAndShowInfoDialog()
        } else if (v == updateView) {
            updateConfig()
        }
    }

    override fun onLongClick(p0: View?): Boolean {
        // createAndShowEnterPasswordDialog()
        return true
    }

    private fun updateConfig(userInteraction: Boolean = false) {
        needRedrawContentAfterReconfigure = true
        if (!orientationLocked) {
            lockOrientation()
            orientationLocked = true
        }
        configUpdater.updateConfig(this, this, userInteraction)
    }

    /* Start UINotifier */

    override fun onConfigUpdateStart() {
    }

    override fun onConfigUpdateServerError(errorText: String?) {
        networkErrorDetails = errorText
        Log.d(Const.LOG_TAG, "Server error: $errorText")
        enterDeviceIdDialog = EnterDeviceIdDialog(
            error = true,
            deviceID = SettingsHelper.getInstance(this).getDeviceId(),
            onClickExit = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    finishAffinity()
                }
                exitProcess(0)
            },
            onClickSave = {
                if (it.isNotEmpty()) {
                    SettingsHelper.getInstance(this).setDeviceId(it)
                    updateConfig(true)
                }
            },
            onClickDetails = {
                showErrorDetails()
            },
        )
        enterDeviceIdDialog?.show(supportFragmentManager,  "EnterDeviceIdDialog")
    }

    override fun onConfigUpdateNetworkError(errorText: String?) {
        if (KioskUtils.isKioskModeRunning(this) && SettingsHelper.getInstance(this).getConfig() != null) {
            interruptResumeFlow = true
            val restoreLauncherIntent = Intent(this@MainActivity, MainActivity::class.java)
            restoreLauncherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(restoreLauncherIntent)
        }
        val settingsHelper = SettingsHelper.getInstance(this)
        // show error dialog
        networkErrorDetails = errorText
        networkErrorDialog = NetworkErrorDialog(
            // showWifiButton = settingsHelper.getConfig() == null || (settingsHelper.getConfig() != null && settingsHelper.getConfig().isShowWifi())
            showWifiButton = settingsHelper.getConfig() == null,
            onClickRetry = {
                updateConfig(true)
            },
            onClickWifi = {
//                if (KioskUtils.isKioskModeRunning(this)) {
//                    val kioskApp: String = settingsHelper.getConfig().getMainApp()
//                    KioskUtils.startCosuKioskMode(kioskApp, this, true)
//                }
                handler.postDelayed({ startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }, 500)
            },
            onClickCancel = {
                if (configFault) {
                    Log.i(
                        Const.LOG_TAG,
                        "networkErrorCancelClicked(): no configuration available, quit"
                    )
                    Toast.makeText(
                        this, getString(
                            R.string.critical_server_failure,
                            getString(R.string.white_app_name)
                        ), Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                Log.i(Const.LOG_TAG, "networkErrorCancelClicked()")
                if (settingsHelper.getConfig() != null) {
                    showContent()
                } else {
                    Log.i(
                        Const.LOG_TAG,
                        "networkErrorCancelClicked(): no configuration available, retrying"
                    )
                    Toast.makeText(this, R.string.empty_configuration, Toast.LENGTH_LONG).show()
                    configFault = true
                    updateConfig(false)
                }
            },
            onClickDetails = {
                showErrorDetails()
            }
        )
        networkErrorDialog?.show(supportFragmentManager, "NetworkErrorDialog")
    }

    override fun onConfigLoaded() {
    }

    override fun onPoliciesUpdated() {
    }

    override fun onDownloadProgress(progress: Int, total: Long, current: Long) {
        CoroutineScope(Dispatchers.Main).launch {
            if (downloadAndInstallAppDialog != null) {
                downloadAndInstallAppDialog?.showDownloadProgress(progress, total, current)
            }
        }
    }

//    override fun onFileDownloading(remoteFile: RemoteFile?) {
//        TODO("Not yet implemented")
//    }
//
//    override fun onDownloadProgress(progress: Int, total: Long, current: Long) {
//        TODO("Not yet implemented")
//    }
//
//    override fun onFileDownloadError(remoteFile: RemoteFile?) {
//        TODO("Not yet implemented")
//    }
//
//    override fun onFileInstallError(remoteFile: RemoteFile?) {
//        TODO("Not yet implemented")
//    }

    override fun onAppUpdateStart() {
    }

    override fun onAppRemoving(application: Application) {
        if (downloadAndInstallAppDialog != null) {
            downloadAndInstallAppDialog?.showAppRemoving(application)
        }
    }

    override fun onAppDownloading(application: Application) {
        if (downloadAndInstallAppDialog == null) {
            downloadAndInstallAppDialog = DownloadAndInstallAppDialog {
                downloadAndInstallAppDialog?.showDownloadApp(application)
            }
            downloadAndInstallAppDialog?.show(supportFragmentManager, "DownloadAndInstallAppDialog")
        } else {
            downloadAndInstallAppDialog?.showDownloadApp(application)
        }
    }

    override fun onAppInstalling(application: Application) {
        if (downloadAndInstallAppDialog != null) {
            downloadAndInstallAppDialog?.showAppInstalling(application)
        }
    }

    override fun onAppDownloadError(application: Application?) {
    }

    override fun onAppInstallError(packageName: String?) {
    }

    override fun onAppInstallComplete(packageName: String?) {
    }

    override fun onConfigUpdateComplete() {
        if (downloadAndInstallAppDialog != null) {
            downloadAndInstallAppDialog?.dismiss()
            downloadAndInstallAppDialog = null
        }
        configInitialized = true
        showContent()
    }

    override fun onAllAppInstallComplete() {
    }

    /* End UINotifier */

    private fun showContent() {
        val config = SettingsHelper.getInstance(this).getConfig()

        if (config == null) {
            Log.e(Const.LOG_TAG, "Config is null")
            return
        }

        createButtons()

        scheduleInstalledAppsRun()

        if (orientationLocked) {
            Utils.setOrientation(this, config)
            orientationLocked = false
        }

        if (config.kioskMode == true && config.kioskApps.isNotEmpty()) {
            if (KioskUtils.isKioskModeRunning(this)) {
                KioskUtils.updateKioskAllowedApps(config.kioskApps, this)
            } else {
                if (startKiosk(config.kioskApps)) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    return
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode failed, proceed with the default flow")
                }
            }
        } else {
            if (KioskUtils.isKioskModeRunning(this)) {
                // Turn off kiosk and show desktop if it is turned off in the configuration
                KioskUtils.unlockKiosk(this)
                // close floating button if needed
                floatingButtonService?.closeFloatingButton()
            }
        }

        // TODO: Somehow binding is null here which causes a crash. Not sure why this could happen.
        if (config?.backgroundColor != null) {
            try {
                mDataBinding.activityMainContentWrapper.setBackgroundColor(Color.parseColor(config.backgroundColor))
            } catch (e: java.lang.Exception) {
                // Invalid color
                e.printStackTrace()
                mDataBinding.activityMainContentWrapper.setBackgroundColor(resources.getColor(R.color.defaultBackground))
            }
        } else {
            mDataBinding.activityMainContentWrapper.setBackgroundColor(resources.getColor(R.color.defaultBackground))
        }

        if (mainAppListAdapter == null || needRedrawContentAfterReconfigure) {
            if (!config.backgroundImageUrl.isNullOrEmpty()) {
                mDataBinding.activityMainBackground.visibility = View.VISIBLE
                Glide.with(this)
                    .load(config.backgroundImageUrl)
                    .listener(object : RequestListener<Drawable> {
                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            return false
                        }

                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            mDataBinding.activityMainBackground.visibility = View.GONE
                            return false
                        }
                    })
                    .into(mDataBinding.activityMainBackground)
            } else {
                mDataBinding.activityMainBackground.visibility = View.GONE
            }

            val display = windowManager.defaultDisplay
            val size = Point()
            display.getSize(size)

            val width = size.x
            val itemWidth = resources.getDimensionPixelSize(R.dimen.app_list_item_size)

            val spanCount = (width * 1.0f / itemWidth).toInt()
            mainAppListAdapter = MainAppListAdapter(this, this, this)
            mainAppListAdapter?.setSpanCount(spanCount)

            mDataBinding.activityMainContent.setLayoutManager(GridLayoutManager(this, spanCount))
            mDataBinding.activityMainContent.setAdapter(mainAppListAdapter)
            mainAppListAdapter?.notifyDataSetChanged()

            mDataBinding.activityBottomLayout.visibility = View.GONE
        } else {
            mainAppListAdapter?.notifyDataSetChanged()
        }

        // We can now sleep, uh
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startKiosk(kioskApps: List<String>): Boolean {
        return KioskUtils.startCosuKioskMode(kioskApps, this@MainActivity)
    }

    /* Start BaseAppListAdapter.OnAppChooseListener */

    override fun onAppChoose(resolveInfo: AppInfo) {

    }

    /* End BaseAppListAdapter.OnAppChooseListener */


    /* Start BaseAppListAdapter.SwitchAdapterListener */

    override fun switchAppListAdapter(adapter: BaseAppListAdapter?, direction: Int): Boolean {
        return true
    }

    /* End BaseAppListAdapter.SwitchAdapterListener */

    private fun lockOrientation() {
        val orientation = resources.configuration.orientation
        val rotation = windowManager.defaultDisplay.rotation
        Log.d(
            Const.LOG_TAG,
            "Lock orientation: orientation=$orientation, rotation=$rotation"
        )
        requestedOrientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (rotation < Surface.ROTATION_180) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        } else {
            if (rotation < Surface.ROTATION_180) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }

    // Run default launcher (Headwind MDM) as if the user clicked Home button
    private fun openDefaultLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    private fun scheduleInstalledAppsRun() {
        val applicationsForRun = configUpdater.getApplicationsForRun()

        if (applicationsForRun.isEmpty()) {
            return
        }
        var pause: Int = PAUSE_BETWEEN_AUTORUNS_SEC
        while (applicationsForRun.isNotEmpty()) {
            val application = applicationsForRun[0]
            applicationsForRun.removeAt(0)
            handler.postDelayed({
                val launchIntent = packageManager.getLaunchIntentForPackage(application.pkg)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }, (pause * 1000).toLong())
            pause += PAUSE_BETWEEN_AUTORUNS_SEC
        }
    }

    companion object {
        private const val PAUSE_BETWEEN_AUTORUNS_SEC = 3
        private var configInitialized: Boolean = false

        // This flag is used to exit kiosk to avoid looping in onResume()
        private var interruptResumeFlow: Boolean = false
    }
}