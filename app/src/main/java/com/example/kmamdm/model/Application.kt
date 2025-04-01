package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class Application(
    @SerializedName("name") var name: String,
    @SerializedName("pkg") val pkg: String,
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("url") val url: String?,
    @SerializedName("iconUrl") val iconUrl: String?,
    @SerializedName("iconText") val iconText: String?,
    @SerializedName("screenOrder") val screenOrder: Int,
    @SerializedName("showIcon") val showIcon: Boolean,
    @SerializedName("remove") val remove: Boolean,
    @SerializedName("runAfterInstall") val runAfterInstall: Boolean,
    @SerializedName("runAtBoot") val runAtBoot: Boolean,
)