package com.example.kmamdm.ui.screen.main

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Handler
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
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
import com.example.kmamdm.pro.KioskUtils
import com.example.kmamdm.ui.adapter.BaseAppListAdapter
import com.example.kmamdm.ui.adapter.MainAppListAdapter
import com.example.kmamdm.ui.dialog.DownloadAndInstallAppDialog
import com.example.kmamdm.ui.screen.base.BaseActivity
import com.example.kmamdm.utils.AppInfo
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : BaseActivity<MainViewModel, ActivityMainBinding>(), View.OnClickListener,
    OnLongClickListener, UINotifier, BaseAppListAdapter.OnAppChooseListener,
    BaseAppListAdapter.SwitchAdapterListener {
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

    override fun createViewModel() = MainViewModel::class.java

    override fun getContentView(): Int = R.layout.activity_main

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        val intent = intent
//        Log.d(
//            Const.LOG_TAG,
//            "MainActivity started" + (if (intent != null && intent.action != null) ", action: " + intent.action else "")
//        )
//        if (intent != null && "android.app.action.PROVISIONING_SUCCESSFUL".equals(
//                intent.action,
//                ignoreCase = true
//            )
//        ) {
//            firstStartAfterProvisioning = true
//        }
//        Initializer.init(this)
//    }
//
//    override fun onResume() {
//        super.onResume()
//        if (firstStartAfterProvisioning) {
//            firstStartAfterProvisioning = false
//            waitForProvisioning(10)
//        } else {
//            setDefaultLauncherEarly()
//        }
//    }
//
//    private fun waitForProvisioning(attempts: Int) {
//        if (Utils.isDeviceOwner(this) || attempts <= 0) {
//            setDefaultLauncherEarly()
//        } else {
//            handler.postDelayed({ waitForProvisioning(attempts - 1) }, 1000)
//        }
//    }
//
//    private fun setDefaultLauncherEarly() {
//        checkAndStartLauncher()
//    }

    override fun initView() {
        createLauncherButtons()
    }

    override fun bindViewModel() {
        updateConfig()
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

    private fun updateConfig() {
        needRedrawContentAfterReconfigure = true
        if (!orientationLocked) {
            lockOrientation()
            orientationLocked = true
        }
        configUpdater.updateConfig(this, this, true)
    }

    /* Start UINotifier */

    override fun onConfigUpdateStart() {
    }

    override fun onConfigUpdateServerError(errorText: String?) {
        showContent()
    }

    override fun onConfigUpdateNetworkError(errorText: String?) {
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

        scheduleInstalledAppsRun()

        // Run default launcher option
        if (config.runDefaultLauncher != null && config.runDefaultLauncher && !packageName.equals(Utils.getDefaultLauncher(this)) && !Utils.isLauncherIntent(intent)
        ) {
            openDefaultLauncher()
            return
        }

        if (orientationLocked) {
            Utils.setOrientation(this, config)
            orientationLocked = false
        }

        if (config.kioskMode == true) {
            val kioskApp = config.mainApp
            if (kioskApp != null && kioskApp.trim().isNotEmpty() &&
                // If KMA MDM itself is set as kiosk app, the kiosk mode is already turned on;
                // So here we just proceed to drawing the content
                (kioskApp != packageName || !KioskUtils.isKioskModeRunning(this))
            ) {
                if (KioskUtils.getKioskAppIntent(kioskApp, this) != null && startKiosk(kioskApp)) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    return
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode failed, proceed with the default flow")
                }
            } else {
                if (kioskApp != null && kioskApp == packageName && KioskUtils.isKioskModeRunning(this)) {
                    // Here we go if the configuration is changed when launcher is in the kiosk mode
                    KioskUtils.updateKioskAllowedApps(kioskApp, this, false)
                } else {
                    Log.e(Const.LOG_TAG, "Kiosk mode disabled: please setup the main app!")
                }
            }
        } else {
            if (KioskUtils.isKioskModeRunning(this)) {
                // Turn off kiosk and show desktop if it is turned off in the configuration
                KioskUtils.unlockKiosk(this)
                openDefaultLauncher()
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
            if (config?.backgroundImageUrl != null && config.backgroundImageUrl.isNotEmpty()) {
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

    private fun startKiosk(kioskApp: String): Boolean {
        return KioskUtils.startCosuKioskMode(kioskApp, this@MainActivity)
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
    }
}