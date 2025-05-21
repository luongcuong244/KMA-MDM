package com.example.kmamdm.utils

import android.content.SharedPreferences
import android.util.Log
import com.example.kmamdm.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date

object PreferenceLogger {
    private val DEBUG: Boolean = true

    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private fun _log(preferences: SharedPreferences, message: String) {
        Log.d(Const.LOG_TAG, message)
        if (DEBUG) {
            var logString = preferences.getString(Const.PREFERENCES_LOG_STRING, "")
            logString += sdf.format(Date()) + " " + message
            logString += "\n"
            preferences.edit().putString(Const.PREFERENCES_LOG_STRING, logString).commit()
        }
    }

    @Synchronized
    fun log(preferences: SharedPreferences, message: String) {
        _log(preferences, message)
    }

    @Synchronized
    fun getLogString(preferences: SharedPreferences): String? {
        if (DEBUG) {
            return preferences.getString(Const.PREFERENCES_LOG_STRING, "")
        }
        return ""
    }

    @Synchronized
    fun clearLogString(preferences: SharedPreferences) {
        if (DEBUG) {
            preferences.edit().putString(Const.PREFERENCES_LOG_STRING, "").commit()
        }
    }

    @Synchronized
    fun printStackTrace(preferences: SharedPreferences, e: Exception) {
        val errors = StringWriter()
        e.printStackTrace(PrintWriter(errors))
        _log(preferences, errors.toString())
    }
}