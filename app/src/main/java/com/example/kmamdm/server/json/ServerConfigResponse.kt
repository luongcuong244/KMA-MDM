package com.example.kmamdm.server.json

import com.example.kmamdm.model.ServerConfig
import com.google.gson.annotations.SerializedName

data class ServerConfigResponse(
    @SerializedName("data") val data: ServerConfig,
)