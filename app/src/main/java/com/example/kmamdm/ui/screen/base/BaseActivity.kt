package com.example.kmamdm.ui.screen.base

import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.kmamdm.extension.showToast
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.server.ServerUrl
import com.example.kmamdm.ui.dialog.EnterDeviceIdDialog
import com.example.kmamdm.ui.dialog.EnterServerUrlDialog
import com.example.kmamdm.ui.dialog.NetworkErrorDialog
import com.example.kmamdm.ui.screen.error_details.ErrorDetailsActivity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.system.exitProcess

abstract class BaseActivity<VM : ViewModel, DB : ViewDataBinding> : AppCompatActivity() {
    lateinit var mViewModel: VM
    lateinit var mDataBinding: DB
    private var currentApiVersion = 0

    protected var enterDeviceIdDialog: EnterDeviceIdDialog? = null
    protected var enterServerUrlDialog: EnterServerUrlDialog? = null
    protected var networkErrorDetails: String? = null

    protected var networkErrorDialog: NetworkErrorDialog? = null

    abstract fun createViewModel(): Class<VM>
    abstract fun getContentView(): Int
    abstract fun initView()
    abstract fun bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        currentApiVersion = Build.VERSION.SDK_INT
        val flags: Int = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        if (currentApiVersion >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = flags
            val decorView: View = window.decorView
            decorView
                .setOnSystemUiVisibilityChangeListener { visibility ->
                    if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                        decorView.systemUiVisibility = flags
                    }
                }
        }
        super.onCreate(savedInstanceState)
        mDataBinding = DataBindingUtil.setContentView(this, getContentView())
        mViewModel = ViewModelProvider(this)[createViewModel()]
        initView()
        bindViewModel()

        Log.d("CHECK_ACTIVITY", "onCreate: ${javaClass.simpleName}")
    }

    fun showActivity(activity: Class<*>, bundle: Bundle? = null) {
        val intent = Intent(this, activity)
        intent.putExtras(bundle ?: Bundle())
        startActivityWithDefaultRequestCode(intent)
    }

    fun showActivity(intent: Intent) {
        startActivityWithDefaultRequestCode(intent)
    }

    private fun startActivityWithDefaultRequestCode(intent: Intent) {
        startActivityForResult(intent, 1000)
    }

    fun showErrorDetails() {
        ErrorDetailsActivity.display(this, networkErrorDetails, false)
    }

    fun createAndShowEnterDeviceDialog(
        onClickSave: (String) -> Unit,
    ) {
        enterDeviceIdDialog = EnterDeviceIdDialog(
            error = true,
            deviceID = SettingsHelper.getInstance(this).getDeviceId(),
            onClickExit = {
                finishAffinity()
                exitProcess(0)
            },
            onClickSave = {
                if (it.isNotEmpty()) {
                    onClickSave.invoke(it)
                }
            },
            onClickDetails = {
                showErrorDetails()
            },
        )
        enterDeviceIdDialog?.show(supportFragmentManager,  "EnterDeviceIdDialog")
    }

    fun createAndShowEnterServerUrlDialog(
        onServerUrlSaved: () -> Unit = {},
    ) {
        enterServerUrlDialog = EnterServerUrlDialog(
            serverUrl = SettingsHelper.getInstance(this).getBaseUrl(),
            onClickSave = { serverUrl ->
                if (saveServerUrlBase(serverUrl)) {
                    enterServerUrlDialog?.dismiss()
                    onServerUrlSaved.invoke()
                }
            },
        )
        enterServerUrlDialog?.show(supportFragmentManager, "EnterServerUrlDialog")
    }

    private fun saveServerUrlBase(serverUrl: String): Boolean {
        var url: ServerUrl? = null
        try {
            url = ServerUrl(serverUrl)

            // Retrofit uses HttpUrl!
            val httpUrl = serverUrl.toHttpUrlOrNull()
            if (httpUrl == null) {
                showToast("Invalid URL format")
                return false
            }
        } catch (e: java.lang.Exception) {
            showToast("Invalid URL format")
            return false
        }
        SettingsHelper.getInstance(this).setBaseUrl(url.baseUrl)
        return true
    }

    protected fun dismissDialog(dialog: Dialog?) {
        if (dialog != null) {
            try {
                dialog.dismiss()
            } catch (ignored: Exception) {
            }
        }
    }
}