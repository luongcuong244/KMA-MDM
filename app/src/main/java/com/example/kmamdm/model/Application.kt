package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class Application(
    @SerializedName("name") var name: String,
    @SerializedName("pkg") val pkg: String,
    @SerializedName("version") val version: String,
)