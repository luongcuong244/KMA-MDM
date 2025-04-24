package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class Application(
    @SerializedName("name") var name: String,
    @SerializedName("pkg") val pkg: String,
    @SerializedName("isSystemApp") val isSystemApp: Boolean,
    @SerializedName("versions") val versions: List<ApplicationVersion>,
    @SerializedName("showIcon") val showIcon: Boolean,
    @SerializedName("icon") val icon: ApplicationIcon?,
    @SerializedName("iconText") val iconText: String?,
)