package com.example.kmamdm.socket

import android.content.Context
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.server.ServerAddress
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

public class SocketSignaling(
    private val eventListener: EventListener,
) {
    private var socket: Socket? = null

    public interface EventListener {
        fun onSocketConnected()
        fun onSocketDisconnected(reason: String)
        fun onError(error: String)
    }

    private object Event {
        const val SOCKET_ERROR = "SOCKET:ERROR"
        const val STREAM_CREATE = "STREAM:CREATE"
        const val STREAM_REMOVE = "STREAM:REMOVE"
        const val STREAM_START = "STREAM:START"
        const val STREAM_STOP = "STREAM:STOP"
        const val HOST_OFFER = "HOST:OFFER"
        const val HOST_CANDIDATE = "HOST:CANDIDATE"
        const val STREAM_JOIN = "STREAM:JOIN"
        const val STREAM_LEAVE = "STREAM:LEAVE"
        const val CLIENT_ANSWER = "CLIENT:ANSWER"
        const val CLIENT_CANDIDATE = "CLIENT:CANDIDATE"
        const val REMOVE_CLIENT = "REMOVE:CLIENT"
        const val CLIENT_CLICK = "CLIENT:CLICK"
        const val CLIENT_SWIPE = "CLIENT:SWIPE"
    }

    private object Payload {
        const val WEB_SOCKET_AUTH_TOKEN = "hostToken"

        const val MESSAGE = "message"
        const val STATUS = "status"
    }

    fun openSocket(context: Context) {
        val deviceID = SettingsHelper.getInstance(context).getDeviceId()
        val options = IO.Options.builder()
            .setReconnection(false) //On Socket.EVENT_DISCONNECT or Socket.EVENT_CONNECT_ERROR or Event.SOCKET_ERROR. Auto or User reconnect
            .setAuth(mapOf(Payload.WEB_SOCKET_AUTH_TOKEN to deviceID)).build()

        socket = IO.socket(ServerAddress.SERVER_ADDRESS, options).apply {
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
            open()
        }
    }

    fun destroy() {
        socket?.off()
        socket?.close()
        socket = null
    }
}