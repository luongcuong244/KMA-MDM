package com.example.kmamdm.utils

import android.content.ComponentName
import android.content.Context
import com.example.kmamdm.AdminReceiver

object LegacyUtils {
    fun getAdminComponentName(context: Context): ComponentName {
        return ComponentName(context.applicationContext, AdminReceiver::class.java)
    }
}