package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class Application(
    @SerializedName("name") var name: String,
    @SerializedName("pkg") val pkg: String,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("url") val url: String?,
    @SerializedName("icon") val icon: String?,
    @SerializedName("iconText") val iconText: String?,
)