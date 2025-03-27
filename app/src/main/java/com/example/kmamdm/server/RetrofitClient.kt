package com.example.kmamdm.server

import android.util.Log
import com.example.kmamdm.utils.SharePrefUtils
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient {

    companion object {

        private var retrofit: Retrofit? = null

        fun getClient(): Retrofit {
            if (retrofit == null) {
                createClient()
            }
            return retrofit!!
        }

        private fun createClient() {
            val httpClient = setupOkHttpClient()

            val gson = GsonBuilder()
                .setLenient()
                .create()

            retrofit = Retrofit.Builder()
                .baseUrl(ServerAddress.SERVER_ADDRESS)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(httpClient)
                .build()
        }

        private fun setupOkHttpClient() : OkHttpClient {
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