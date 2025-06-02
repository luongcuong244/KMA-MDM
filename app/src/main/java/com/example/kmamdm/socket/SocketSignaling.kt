package com.example.kmamdm.socket

import android.content.Context
import android.util.Log
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.server.ServerAddress
import com.example.kmamdm.socket.json.DeviceStatus
import com.example.kmamdm.socket.json.PushMessage
import com.google.gson.Gson
import io.socket.client.Ack
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
        fun onReceiveViewDeviceStatus(): DeviceStatus
        fun onReceivePushMessages(webSocketId: String, messages: List<PushMessage>)
        fun onReceiveRequestRemoteControl(webSocketId: String)
    }

    private object Event {
        const val SOCKET_ERROR = "SOCKET:ERROR"
        const val MOBILE_RECEIVE_VIEW_DEVICE_STATUS = "mobile:receive:view_device_status"
        const val MOBILE_RECEIVE_PUSH_MESSAGES = "mobile:receive:push_messages"
        const val MOBILE_SEND_PUSH_MESSAGES = "mobile:send:push_messages"
        const val MOBILE_RECEIVE_REQUEST_REMOTE_CONTROL = "mobile:receive:request_remote_control"
        const val MOBILE_SEND_ACCEPT_REMOTE_CONTROL = "mobile:send:accept_remote_control"
    }

    private object Payload {
        const val WEB_SOCKET_AUTH_TOKEN = "hostToken"

        const val MESSAGE = "message"
        const val WEB_SOCKET_ID = "webSocketId"
        const val DEVICE_STATUS = "deviceStatus"
        const val MESSAGES = "messages"
        const val DEVICE_ID = "deviceId"
        const val ERROR_MESSAGE = "errorMessage"
        const val STATUS = "status"
        const val SUCCESS = "success"
        const val ERROR = "error"
        const val DATA = "data"
    }


    fun openSocket(context: Context) {
        val deviceID = SettingsHelper.getInstance(context).getDeviceId()
        val options = IO.Options.builder()
            .setReconnection(true) //On Socket.EVENT_DISCONNECT or Socket.EVENT_CONNECT_ERROR or Event.SOCKET_ERROR. Auto or User reconnect
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
                val payload = SocketPayload.fromPayload(args)
                val webSocketId = payload.json?.optString(Payload.WEB_SOCKET_ID)
                if (webSocketId.isNullOrEmpty()) {
                    payload.sendErrorAck("WebSocket ID is missing")
                    return@on
                }
                val deviceStatus = eventListener.onReceiveViewDeviceStatus()
                val gson = Gson()
                val jsonObject = JSONObject()
                jsonObject.put(Payload.DEVICE_STATUS, gson.toJson(deviceStatus))
                payload.sendOkAck(jsonObject)
            }
            on(Event.MOBILE_RECEIVE_PUSH_MESSAGES) { args ->
                val payload = SocketPayload.fromPayload(args)
                try {
                    val webSocketId = payload.json?.optString(Payload.WEB_SOCKET_ID, "")
                    val messagesJsonArray = payload.json?.optJSONArray(Payload.MESSAGES)

                    if (webSocketId.isNullOrEmpty()) {
                        payload.sendErrorAck("WebSocket ID is missing")
                        return@on
                    }

                    if (messagesJsonArray == null) {
                        payload.sendErrorAck("Messages array is missing")
                        return@on
                    }

                    val messages: List<PushMessage> =
                        Gson().fromJson(messagesJsonArray.toString(), Array<PushMessage>::class.java).toList()

                    if (messages.isEmpty()) {
                        payload.sendErrorAck("No messages received")
                        return@on
                    }

                    eventListener.onReceivePushMessages(webSocketId, messages)

                    payload.sendOkAck()
                } catch (e: Exception) {
                    Log.e("SocketSignaling", "Error parsing push messages: ${e.message}", e)
                    payload.sendErrorAck("Error parsing push messages: ${e.message}")
                }
            }
            on(Event.MOBILE_RECEIVE_REQUEST_REMOTE_CONTROL) { args ->
                val webSocketId = (args?.firstOrNull() as? JSONObject)?.optString(Payload.WEB_SOCKET_ID) ?: ""
                eventListener.onReceiveRequestRemoteControl(webSocketId)
            }
            open()
        }
    }

    fun sendAcceptRemoteControl(webSocketId: String, deviceId: String, errorMessage: String? = null) {
        val jsonObject = JSONObject()
        jsonObject.put(Payload.WEB_SOCKET_ID, webSocketId)
        jsonObject.put(Payload.DEVICE_ID, deviceId)
        if (errorMessage != null) {
            jsonObject.put(Payload.ERROR_MESSAGE, errorMessage)
        }
        socket?.emit(Event.MOBILE_SEND_ACCEPT_REMOTE_CONTROL, jsonObject)
    }

    fun destroy() {
        socket?.off()
        socket?.close()
        socket = null
    }

    private class SocketPayload(val json: JSONObject?, private val ack: Ack?) {
        companion object {
            internal fun fromPayload(payload: Array<Any?>?): SocketPayload =
                SocketPayload(payload?.firstOrNull() as? JSONObject, payload?.lastOrNull() as? Ack)
        }

        fun sendOkAck(json: JSONObject? = null) {
            ack?.call(
                JSONObject().apply {
                    put(Payload.STATUS, Payload.SUCCESS)
                    if (json != null) {
                        put(Payload.DATA, json)
                    }
                }
            )
        }

        fun sendErrorAck(message: String) = ack?.call(JSONObject().apply {
            put(Payload.STATUS, Payload.ERROR)
            put(Payload.MESSAGE, message)
        })
    }
}