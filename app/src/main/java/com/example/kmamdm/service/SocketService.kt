package com.example.kmamdm.service

import android.app.Dialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.kmamdm.R
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.socket.SocketManager
import com.example.kmamdm.socket.SocketSignaling
import com.example.kmamdm.socket.json.PushMessage
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.DeviceUtils
import com.example.kmamdm.worker.PushNotificationProcessor
import java.util.Timer
import java.util.TimerTask

class SocketService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var countdownTimer: CountDownTimer? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SocketManager.initialize(object : SocketSignaling.EventListener {
            override fun onSocketConnected() {
                SocketManager.isSocketConnected = true
                LocalBroadcastManager.getInstance(this@SocketService).sendBroadcast(
                    Intent(Const.SocketAction.SOCKET_CONNECTED)
                )
            }

            override fun onSocketDisconnected(reason: String) {
                SocketManager.isSocketConnected = false
                LocalBroadcastManager.getInstance(this@SocketService).sendBroadcast(
                    Intent(Const.SocketAction.SOCKET_DISCONNECTED)
                        .putExtra("reason", reason)
                )
            }

            override fun onError(error: String) {
                LocalBroadcastManager.getInstance(this@SocketService).sendBroadcast(
                    Intent(Const.SocketAction.SOCKET_ERROR)
                        .putExtra("error", error)
                )
            }

            override fun onReceiveViewDeviceStatus(webSocketId: String) {
                val deviceStatus = DeviceUtils.getDeviceStatus(this@SocketService)
                SocketManager.get().sendDeviceStatus(webSocketId, deviceStatus)
            }

            override fun onReceivePushMessages(webSocketId: String, messages: List<PushMessage>) {
                for (message in messages) {
                    PushNotificationProcessor.process(message, this@SocketService)
                }
                SocketManager.get().sendPushMessages(webSocketId, messages)
            }

            override fun onReceiveRequestRemoteControl(webSocketId: String) {
                // show dialog thông báo cho người dùng rằng admin muốn điều khiển thiết bị này
                showOverlayRemoteDialog(webSocketId)
            }
        })
        return START_STICKY
    }

    private fun showOverlayRemoteDialog(webSocketId: String) {
        Handler(Looper.getMainLooper()).post {
            if (overlayView != null) return@post

            if (!Settings.canDrawOverlays(this)) {
                SocketManager.get().sendAcceptRemoteControl(webSocketId, "", "Ứng dụng MDM không thể hiển thị dialog confirm vì không có quyền overlay")
                return@post
            }

            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            overlayView = inflater.inflate(R.layout.dialog_remote_control, null)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager.addView(overlayView, params)

            val btnOk = overlayView!!.findViewById<Button>(R.id.btnOk)
            val title = overlayView!!.findViewById<TextView>(R.id.title)

            val timer = object : CountDownTimer(10_000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val seconds = millisUntilFinished / 1000
                    title.text = "Request Remote Control ( ${seconds}s )"
                }

                override fun onFinish() {
                    btnOk.performClick()
                }
            }.start()

            btnOk.setOnClickListener {
                timer.cancel()
                removeOverlayDialog()
                // Xử lý gửi socket xác nhận và launch app ở đây
                var errorMessage: String? = null
                val deviceId = SettingsHelper.getInstance(this).getDeviceId() ?: ""
                if (deviceId.isEmpty()) {
                    errorMessage = "Device ID chưa được thiết lập trên điện thoại."
                }
                val launchIntent = packageManager.getLaunchIntentForPackage(Const.APUPPET_PACKAGE_NAME)
                if (launchIntent != null) {
                    launchIntent.putExtra("deviceId", deviceId)
                    startActivity(launchIntent)
                } else {
                    errorMessage = "Ứng dụng Remote không được cài đặt trên thiết bị này."
                }

                SocketManager.get().sendAcceptRemoteControl(webSocketId, deviceId, errorMessage)
            }
        }
    }

    private fun removeOverlayDialog() {
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
    
    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, SocketService::class.java)
            context.startService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, SocketService::class.java)
            context.stopService(intent)
        }
    }
}