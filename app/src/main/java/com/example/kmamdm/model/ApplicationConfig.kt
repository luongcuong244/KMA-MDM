package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class ApplicationConfig(
    @SerializedName("application") val application: Application,
    @SerializedName("version") val version: ApplicationVersion,
    @SerializedName("screenOrder") val screenOrder: Int,
    @SerializedName("showIcon") val showIcon: Boolean,
    @SerializedName("remove") val remove: Boolean,
    @SerializedName("runAfterInstall") val runAfterInstall: Boolean,
    @SerializedName("runAtBoot") val runAtBoot: Boolean,
)