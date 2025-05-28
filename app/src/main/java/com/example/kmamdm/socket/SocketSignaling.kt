package com.example.kmamdm.socket

import android.content.Context
import android.util.Log
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.server.ServerAddress
import com.example.kmamdm.socket.json.DeviceStatus
import com.example.kmamdm.socket.json.PushMessage
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class SocketSignaling(
    private val eventListener: EventListener,
) {
    private var socket: Socket? = null

    interface EventListener {
        fun onSocketConnected()
        fun onSocketDisconnected(reason: String)
        fun onError(error: String)
        fun onReceiveViewDeviceStatus(webSocketId: String)
        fun onReceivePushMessages(webSocketId: String, messages: List<PushMessage>)
    }

    private object Event {
        const val SOCKET_ERROR = "SOCKET:ERROR"
        const val MOBILE_RECEIVE_VIEW_DEVICE_STATUS = "mobile:receive:view_device_status"
        const val MOBILE_SEND_DEVICE_STATUS = "mobile:send:device_status"
        const val MOBILE_RECEIVE_PUSH_MESSAGES = "mobile:receive:push_messages"
        const val MOBILE_SEND_PUSH_MESSAGES = "mobile:send:push_messages"
    }

    private object Payload {
        const val WEB_SOCKET_AUTH_TOKEN = "hostToken"

        const val MESSAGE = "message"
        const val WEB_SOCKET_ID = "webSocketId"
        const val DEVICE_STATUS = "deviceStatus"
        const val MESSAGES = "messages"
    }

    fun openSocket(context: Context) {
        val deviceID = SettingsHelper.getInstance(context).getDeviceId()
        val options = IO.Options.builder()
            .setReconnection(false) //On Socket.EVENT_DISCONNECT or Socket.EVENT_CONNECT_ERROR or Event.SOCKET_ERROR. Auto or User reconnect
            .setAuth(mapOf(Payload.WEB_SOCKET_AUTH_TOKEN to deviceID)).build()

        val serverUrl = SettingsHelper.getInstanceOrNull()?.getBaseUrl() ?: ServerAddress.SERVER_ADDRESS

        socket = IO.socket(serverUrl, options).apply {
            on(Socket.EVENT_CONNECT) {
                eventListener.onSocketConnected()
            }
            on(Socket.EVENT_DISCONNECT) { args -> // Auto reconnect
                eventListener.onSocketDisconnected(args.contentToString())
            }
            on(Socket.EVENT_CONNECT_ERROR) { args -> // Auto or User reconnect
                val message = (args?.firstOrNull() as? JSONObject)?.optString(Payload.MESSAGE) ?: ""
                eventListener.onError(message)
            }
            on(Event.SOCKET_ERROR) { args -> // Server always disconnects socket on this event. User reconnect
                val message = (args?.firstOrNull() as? JSONObject)?.optString(Payload.MESSAGE) ?: ""
                eventListener.onError(message)
            }
            on(Event.MOBILE_RECEIVE_VIEW_DEVICE_STATUS) { args ->
                val webSocketId = (args?.firstOrNull() as? JSONObject)?.optString(Payload.WEB_SOCKET_ID) ?: ""
                eventListener.onReceiveViewDeviceStatus(webSocketId)
            }
            on(Event.MOBILE_RECEIVE_PUSH_MESSAGES) { args ->
                try {
                    val jsonObject = args?.firstOrNull() as? JSONObject ?: return@on

                    val webSocketId = jsonObject.optString(Payload.WEB_SOCKET_ID, "")
                    val messagesJsonArray = jsonObject.optJSONArray(Payload.MESSAGES)

                    val messages: List<PushMessage> = if (messagesJsonArray != null) {
                        Gson().fromJson(messagesJsonArray.toString(), Array<PushMessage>::class.java).toList()
                    } else {
                        emptyList()
                    }

                    eventListener.onReceivePushMessages(webSocketId, messages)
                } catch (e: Exception) {
                    Log.e("SocketSignaling", "Error parsing push messages: ${e.message}", e)
                }
            }
            open()
        }
    }

    fun sendDeviceStatus(webSocketId: String, deviceStatus: DeviceStatus) {
        val gson = Gson()
        val jsonObject = JSONObject()
        jsonObject.put(Payload.WEB_SOCKET_ID, webSocketId)
        jsonObject.put(Payload.DEVICE_STATUS, gson.toJson(deviceStatus))
        socket?.emit(Event.MOBILE_SEND_DEVICE_STATUS, jsonObject)
    }

    fun sendPushMessages(webSocketId: String, messages: List<PushMessage>) {
        val gson = Gson()
        val jsonObject = JSONObject()
        jsonObject.put(Payload.WEB_SOCKET_ID, webSocketId)
        jsonObject.put(Payload.MESSAGES, gson.toJson(messages))
        socket?.emit(Event.MOBILE_SEND_PUSH_MESSAGES, jsonObject)
    }

    fun destroy() {
        socket?.off()
        socket?.close()
        socket = null
    }
}