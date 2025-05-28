package com.example.kmamdm.socket.json

import com.google.gson.annotations.SerializedName

data class PushMessage(
    @SerializedName("_id") val id: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("messageType") val messageType: String,
    @SerializedName("payload") val payload: Map<String, Any>?
) {
    companion object {
        const val TYPE_CONFIG_UPDATED: String = "configUpdated"
        const val TYPE_RUN_APP: String = "runApp"
        const val TYPE_UNINSTALL_APP: String = "uninstallApp"
        const val TYPE_DELETE_FILE: String = "deleteFile"
        const val TYPE_PURGE_DIR: String = "purgeDir"
        const val TYPE_DELETE_DIR: String = "deleteDir"
        const val TYPE_PERMISSIVE_MODE: String = "permissiveMode"
        const val TYPE_RUN_COMMAND: String = "runCommand"
        const val TYPE_REBOOT: String = "reboot"
        const val TYPE_EXIT_KIOSK: String = "exitKiosk"
        const val TYPE_CLEAR_DOWNLOADS: String = "clearDownloadHistory"
        const val TYPE_SETTINGS: String = "settings"
    }
}