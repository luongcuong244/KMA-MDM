package com.example.kmamdm.model

import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.server.ServerAddress
import com.google.gson.annotations.SerializedName

data class ApplicationIcon(
    @SerializedName("name") val name: String,
    @SerializedName("url") private val url: String,
) {
    val fullUrl: String
        get() = url.let {
            if (it.startsWith("http")) {
                it
            }
            else {
                val serverUrl = SettingsHelper.getInstanceOrNull()?.getBaseUrl() ?: ServerAddress.SERVER_ADDRESS
                serverUrl + it
            }
        }
}