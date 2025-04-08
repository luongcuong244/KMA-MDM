package com.example.kmamdm.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.example.kmamdm.utils.Const

class FloatingButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingButton: View? = null

    private val binder: IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: FloatingButtonService
            get() = this@FloatingButtonService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        showFloatingButton()
        Log.d(Const.LOG_TAG, "Floating button created")
    }

    fun showFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?

        floatingButton = Button(this)
        (floatingButton as Button).text = "Exit Kiosk"

        val layoutFlag = if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.O))
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        params.x = 0

        windowManager!!.addView(floatingButton, params)
    }

    fun setFloatingButtonOnClickListener(listener: View.OnClickListener) {
        floatingButton?.setOnClickListener(listener)
    }

    fun closeFloatingButton() {
        if (floatingButton != null && windowManager != null) {
            windowManager!!.removeView(floatingButton)
            floatingButton = null
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingButton != null && windowManager != null) {
            windowManager!!.removeView(floatingButton)
        }
    }
}