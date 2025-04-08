package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class ApplicationSetting(
    @SerializedName("packageId") var packageId: String,
    @SerializedName("attribute") var attribute: String,
    @SerializedName("value") var value: String,
    @SerializedName("comment") var comment: String,
)