package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class ServerConfig(
    // Common Setting
    @SerializedName("gps") val gps: String?,
    @SerializedName("bluetooth") val bluetooth: String?,
    @SerializedName("wifi") val wifi: String?,
    @SerializedName("mobileData") val mobileData: String?,

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
) {
    companion object {
        const val APP_PERMISSIONS_ASK_LOCATION: String = "asklocation"
        const val APP_PERMISSIONS_DENY_LOCATION: String = "denylocation"
        const val APP_PERMISSIONS_ASK_ALL: String = "askall"
    }
}