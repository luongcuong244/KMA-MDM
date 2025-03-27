package com.example.kmamdm.utils

import android.content.Context
import android.content.SharedPreferences

object SharePrefUtils {
    private var mSharePref: SharedPreferences? = null
    fun init(context: Context) {
        if (mSharePref == null) {
            mSharePref = context.getSharedPreferences("data", Context.MODE_PRIVATE);
        }
    }

    fun saveAccessToken(accessToken: String): Boolean {
        val editor = mSharePref!!.edit()
        editor.putString("accessToken", accessToken)
        return editor.commit()
    }

    fun getAccessToken(): String? {
        return mSharePref!!.getString("accessToken", null)
    }

    fun saveRefreshToken(refreshToken: String): Boolean {
        val editor = mSharePref!!.edit()
        editor.putString("refreshToken", refreshToken)
        return editor.commit()
    }

    fun getRefreshToken(): String? {
        return mSharePref!!.getString("refreshToken", null)
    }
}