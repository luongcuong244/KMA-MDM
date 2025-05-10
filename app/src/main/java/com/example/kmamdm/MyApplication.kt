package com.example.kmamdm

import android.app.Application
import com.example.kmamdm.utils.SharePrefUtils

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SharePrefUtils.init(this)
    }
}