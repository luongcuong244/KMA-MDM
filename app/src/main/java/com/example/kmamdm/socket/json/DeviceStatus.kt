package com.example.kmamdm.socket.json

import com.example.kmamdm.model.DeviceInfo
import com.google.gson.annotations.SerializedName

data class DeviceStatus(
    @SerializedName("deviceInfo") val deviceInfo: DeviceInfo,
    @SerializedName("batteryLevel") val batteryLevel: Int,
    @SerializedName("batteryStatus") val batteryStatus: String,
    @SerializedName("isCharging") val isCharging: Boolean,
    @SerializedName("storageUsage") val storageUsage: Long, // dung lượng bộ nhớ đã dùng
    @SerializedName("ramUsage") val ramUsage: Long, // RAM đang sử dụng
    @SerializedName("isLocked") val isLocked: Boolean, // Thiết bị có đang bị khóa
    @SerializedName("networkType") val networkType: String, // Loại mạng đang kết nối (WiFi, LTE, 5G,...)
    @SerializedName("locationEnabled") val locationEnabled: Boolean, // Dịch vụ định vị đang bật hay tắt
    @SerializedName("location") val location: String, // Vị trí hiện tại của thiết bị (latitude, longitude)
)
