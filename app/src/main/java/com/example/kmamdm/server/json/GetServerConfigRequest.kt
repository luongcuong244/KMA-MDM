package com.example.kmamdm.server.json

import com.example.kmamdm.model.DeviceInfo
import com.google.gson.annotations.SerializedName

data class GetServerConfigRequest(
    @SerializedName("signature") val signature: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("deviceInfo") val deviceInfo: DeviceInfo,
)