package com.example.kmamdm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.kmamdm.socket.SocketManager
import com.example.kmamdm.socket.SocketSignaling
import com.example.kmamdm.utils.Const
import com.example.kmamdm.utils.DeviceUtils

class SocketService : Service() {
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
        })
        return START_STICKY
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