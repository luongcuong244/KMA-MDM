package com.example.kmamdm.socket.json

import com.example.kmamdm.model.DeviceInfo
import com.google.gson.annotations.SerializedName

data class DeviceStatus(
    @SerializedName("webSocketId") val webSocketId: String,
    @SerializedName("deviceInfo") val deviceInfo: DeviceInfo,
    @SerializedName("batteryLevel") val batteryLevel: Int,
    @SerializedName("batteryStatus") val batteryStatus: String,
    @SerializedName("isCharging") val isCharging: Boolean,
)
