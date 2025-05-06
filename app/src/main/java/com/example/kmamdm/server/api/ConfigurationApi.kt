package com.example.kmamdm.server.api

import com.example.kmamdm.model.DeviceInfo
import com.example.kmamdm.server.json.ServerConfigResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ConfigurationApi {
    @POST("/configuration/get-server-config")
    fun getServerConfig(
        @Header("X-Request-Signature") signature: String,
        @Body deviceInfo: DeviceInfo
    ): Call<ServerConfigResponse>
}