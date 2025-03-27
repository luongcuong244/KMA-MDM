package com.example.kmamdm.server.repository

import com.example.kmamdm.server.RetrofitClient
import com.example.kmamdm.server.api.ConfigurationApi
import com.example.kmamdm.server.json.ServerConfigResponse
import retrofit2.Callback

object ConfigurationRepository {
    private val configurationApi : ConfigurationApi = RetrofitClient.getClient().create(ConfigurationApi::class.java)

    fun getServerConfig(callback: Callback<ServerConfigResponse>) {
        configurationApi.getServerConfig().enqueue(callback)
    }
}