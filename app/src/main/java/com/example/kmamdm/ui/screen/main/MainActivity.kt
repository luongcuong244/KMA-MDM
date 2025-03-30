package com.example.kmamdm.ui.screen.main

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
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
import com.example.kmamdm.BuildConfig
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ActivityMainBinding
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.helper.ConfigUpdater.UINotifier
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.model.Application
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

    private var downloadAndInstallAppDialog: DownloadAndInstallAppDialog? = null

    override fun createViewModel() = MainViewModel::class.java

    override fun getContentView(): Int = R.layout.activity_main

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

    override fun onAppRemoving(application: Application?) {
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

        if (orientationLocked) {
            if (config != null) {
                Utils.setOrientation(this, config)
            }
            orientationLocked = false
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
        }

        if (mainAppListAdapter == null) {
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
}