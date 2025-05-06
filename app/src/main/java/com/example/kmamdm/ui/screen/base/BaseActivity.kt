package com.example.kmamdm.ui.screen.base

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
import com.example.kmamdm.ui.dialog.EnterDeviceIdDialog
import com.example.kmamdm.ui.dialog.NetworkErrorDialog
import com.example.kmamdm.ui.screen.error_details.ErrorDetailsActivity

abstract class BaseActivity<VM : ViewModel, DB : ViewDataBinding> : AppCompatActivity() {
    lateinit var mViewModel: VM
    lateinit var mDataBinding: DB
    private var currentApiVersion = 0

    protected var enterDeviceIdDialog: EnterDeviceIdDialog? = null
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
}