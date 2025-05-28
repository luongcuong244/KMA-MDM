package com.example.kmamdm.server

import android.util.Log
import com.example.kmamdm.helper.SettingsHelper
import com.example.kmamdm.utils.SharePrefUtils
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient {

    companion object {

        private var retrofit: Retrofit? = null
        private var currentBaseUrl: String? = null

        fun getClient(): Retrofit {
            val savedUrl =
                SettingsHelper.getInstanceOrNull()?.getBaseUrl() ?: ServerAddress.SERVER_ADDRESS

            if (retrofit == null || savedUrl != currentBaseUrl) {
                createClient(savedUrl)
            }

            return retrofit!!
        }

        private fun createClient(baseUrl: String) {
            val httpClient = setupOkHttpClient()

            val gson = GsonBuilder()
                .setLenient()
                .create()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(httpClient)
                .build()
        }

        private fun setupOkHttpClient(): OkHttpClient {
            val httpClient = OkHttpClient.Builder()

            httpClient.addInterceptor { chain ->
                val originalRequest: Request = chain.request()

                val accessToken = SharePrefUtils.getAccessToken()

                Log.d("RetrofitClient", "accessToken: $accessToken")

                val builder: Request.Builder = originalRequest.newBuilder().header(
                    "Authorization",
                    "Bearer $accessToken"
                )
                val newRequest: Request = builder.build()
                chain.proceed(newRequest)
            }

            return httpClient.build()
        }
    }
}