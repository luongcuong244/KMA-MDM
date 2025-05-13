package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class ServerConfig(
    // Common Setting
    @SerializedName("gps") val gps: String?,
    @SerializedName("bluetooth") val bluetooth: String?,
    @SerializedName("wifi") val wifi: String?,
    @SerializedName("mobileData") val mobileData: String?,
    @SerializedName("blockUSBStorage") val blockUSBStorage: Boolean?,
    @SerializedName("manageScreenTimeout") val manageScreenTimeout: Boolean?,
    @SerializedName("screenTimeout") val screenTimeout: Int?,
    @SerializedName("lockVolume") val lockVolume: Boolean?,
    @SerializedName("manageVolume") val manageVolume: Boolean?,
    @SerializedName("volumeValue") val volumeValue: Int?,
    @SerializedName("disableScreenCapture") val disableScreenCapture: Boolean?,

    @SerializedName("applications") val applications: List<ApplicationConfig>,
    @SerializedName("applicationSettings") val applicationSettings: List<ApplicationSetting> = listOf(),

    @SerializedName("backgroundColor") val backgroundColor: String?,
    @SerializedName("backgroundImageUrl") val backgroundImageUrl: String?,
    @SerializedName("textColor") val textColor: String?,
    @SerializedName("iconSize") val iconSize: Int = 100,
    @SerializedName("orientation") val orientation: Int = 0,
    @SerializedName("runDefaultLauncher") val runDefaultLauncher: Boolean?,
    @SerializedName("restrictions") val restrictions: String?,

    @SerializedName("kioskMode") var kioskMode: Boolean?,
    @SerializedName("kioskApps") val kioskApps: List<String> = listOf(),

    // Other Settings
    @SerializedName("factoryReset") val factoryReset: Boolean?,
    @SerializedName("reboot") val reboot: Boolean?,
    @SerializedName("lock") val lock: Boolean?,
    @SerializedName("lockMessage") val lockMessage: String?,
    @SerializedName("passwordReset") val passwordReset: String?,
)