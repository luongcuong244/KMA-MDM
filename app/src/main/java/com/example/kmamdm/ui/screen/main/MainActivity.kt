package com.example.kmamdm.ui.screen.main

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Point
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.example.kmamdm.R
import com.example.kmamdm.databinding.ActivityMainBinding
import com.example.kmamdm.helper.ConfigUpdater
import com.example.kmamdm.helper.ConfigUpdater.UINotifier
import com.example.kmamdm.model.Application
import com.example.kmamdm.server.json.ServerConfigResponse
import com.example.kmamdm.server.repository.ConfigurationRepository
import com.example.kmamdm.ui.adapter.AppShortcutManager
import com.example.kmamdm.ui.adapter.BaseAppListAdapter
import com.example.kmamdm.ui.adapter.MainAppListAdapter
import com.example.kmamdm.ui.dialog.DownloadAndInstallAppDialog
import com.example.kmamdm.ui.screen.base.BaseActivity
import com.example.kmamdm.utils.AppInfo
import com.example.kmamdm.utils.Const
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : BaseActivity<MainViewModel, ActivityMainBinding>(), View.OnClickListener,
    OnLongClickListener, UINotifier, BaseAppListAdapter.OnAppChooseListener,
    BaseAppListAdapter.SwitchAdapterListener {
    private var exitView: ImageView? = null
    private var infoView: ImageView? = null
    private var updateView: ImageView? = null
    private var mainAppListAdapter: MainAppListAdapter? = null

    private val configUpdater = ConfigUpdater()

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
        configUpdater.updateConfig(this, this, true)
    }

    /* Start UINotifier */

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

            val bottomAppCount: Int = AppShortcutManager.instance!!.getInstalledAppCount(this, true)
//            if (bottomAppCount > 0) {
//                bottomAppListAdapter = BottomAppListAdapter(this, this, this)
//                bottomAppListAdapter.setSpanCount(spanCount)
//
//                binding.activityBottomLayout.setVisibility(View.VISIBLE)
//                binding.activityBottomLine.setLayoutManager(
//                    GridLayoutManager(
//                        this,
//                        if (bottomAppCount < spanCount) bottomAppCount else spanCount
//                    )
//                )
//                binding.activityBottomLine.setAdapter(bottomAppListAdapter)
//                bottomAppListAdapter.notifyDataSetChanged()
//            } else {
//                bottomAppListAdapter = null
//                binding.activityBottomLayout.setVisibility(View.GONE)
//            }
            mDataBinding.activityBottomLayout.visibility = View.GONE
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
}