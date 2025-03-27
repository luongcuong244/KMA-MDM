package com.example.kmamdm.server.api

import com.example.kmamdm.server.json.ServerConfigResponse
import retrofit2.Call
import retrofit2.http.GET

interface ConfigurationApi {
    @GET("/configuration/get-server-config")
    fun getServerConfig(): Call<ServerConfigResponse>
}