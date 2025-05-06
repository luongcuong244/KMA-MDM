package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class DeviceInfo(
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("deviceModel") val deviceModel: String,
    @SerializedName("deviceBrand") val deviceBrand: String,
    @SerializedName("deviceProduct") val deviceProduct: String,
    @SerializedName("deviceManufacturer") val deviceManufacturer: String,
    @SerializedName("deviceSerial") val deviceSerial: String,
    @SerializedName("deviceHardware") val deviceHardware: String,
    @SerializedName("deviceBuildId") val deviceBuildId: String,
    @SerializedName("androidVersion") val androidVersion: String,
    @SerializedName("androidSdkVersion") val androidSdkVersion: Int,
    @SerializedName("androidId") val androidId: String,
    @SerializedName("imei") val imei: String?,
    @SerializedName("cpuArch") val cpuArch: String,
    @SerializedName("cpuCores") val cpuCores: Int,
    @SerializedName("totalRAM") val totalRAM: Long,
    @SerializedName("totalStorage") val totalStorage: Long
)