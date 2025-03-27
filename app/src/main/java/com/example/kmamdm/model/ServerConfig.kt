package com.example.kmamdm.model

import com.google.gson.annotations.SerializedName

data class ServerConfig(
    @SerializedName("allowedApplications") val applications: List<Application>,
)