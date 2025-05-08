package com.example.kmamdm.socket

object SocketManager {
    var isSocketConnected: Boolean = false

    private var signaling: SocketSignaling? = null

    fun initialize(eventListener: SocketSignaling.EventListener) {
        if (signaling == null) {
            signaling = SocketSignaling(eventListener)
        }
    }

    fun get(): SocketSignaling {
        return signaling
            ?: throw IllegalStateException("SocketManager is not initialized. Call initialize() first.")
    }

    fun destroy() {
        signaling?.destroy()
        signaling = null
    }
}