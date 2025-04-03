package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class ServerConfig(
    @SerializedName("allowedApplications") val applications: List<Application>,
    @SerializedName("backgroundColor") val backgroundColor: String?,
    @SerializedName("backgroundImageUrl") val backgroundImageUrl: String?,
    @SerializedName("textColor") val textColor: String?,
    @SerializedName("iconSize") val iconSize: Int = 100,
    @SerializedName("orientation") val orientation: Int = 0,
    @SerializedName("runDefaultLauncher") val runDefaultLauncher: Boolean?,
)