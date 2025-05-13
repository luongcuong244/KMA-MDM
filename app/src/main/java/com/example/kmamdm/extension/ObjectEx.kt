package com.example.kmamdm.extension

import android.util.Log

fun Any.log(message: String) {
    Log.d(this::class.java.simpleName, message)
}