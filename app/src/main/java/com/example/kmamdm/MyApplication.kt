package com.example.kmamdm

import android.app.Application
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.socket.SocketManager
import com.example.kmamdm.socket.SocketSignaling
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.SharePrefUtils

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SharePrefUtils.init(this)
        SocketManager.initialize(object : SocketSignaling.EventListener {
            override fun onSocketConnected() {
                SocketManager.isSocketConnected = true
                LocalBroadcastManager.getInstance(this@MyApplication).sendBroadcast(
                    Intent(Const.SocketAction.SOCKET_CONNECTED)
                )
            }

            override fun onSocketDisconnected(reason: String) {
                SocketManager.isSocketConnected = false
                LocalBroadcastManager.getInstance(this@MyApplication).sendBroadcast(
                    Intent(Const.SocketAction.SOCKET_DISCONNECTED)
                        .putExtra("reason", reason)
                )
            }

            override fun onError(error: String) {
                LocalBroadcastManager.getInstance(this@MyApplication).sendBroadcast(
                    Intent(Const.SocketAction.SOCKET_ERROR)
                        .putExtra("error", error)
                )
            }
        })
    }
}